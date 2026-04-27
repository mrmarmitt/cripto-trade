package com.marmitt.core.application.usecase.boot;

import com.marmitt.core.application.usecase.runner.RunnerBootRecoveryUseCase;
import com.marmitt.core.application.usecase.runner.orderconciliation.ConciliationOrderUpdateExecutor;
import com.marmitt.core.domain.Symbol;
import com.marmitt.core.domain.portfolio.DeadLetterEntry;
import com.marmitt.core.domain.portfolio.GlobalBalance;
import com.marmitt.core.domain.portfolio.Portfolio;
import com.marmitt.core.domain.runner.ClientOrderId;
import com.marmitt.core.domain.runner.StrategyRunner;
import com.marmitt.core.domain.runner.Transaction;
import com.marmitt.core.dto.boot.BootExecutionCommand;
import com.marmitt.core.dto.boot.BootExecutionSummary;
import com.marmitt.core.dto.exchange.boot.ExchangeBootReadiness;
import com.marmitt.core.dto.websocket.data.AccountDataDto;
import com.marmitt.core.dto.websocket.data.OrderDataDto;
import com.marmitt.core.dto.portfolio.PortfolioBootSanityResult;
import com.marmitt.core.dto.portfolio.PortfolioReservationTtlResult;
import com.marmitt.core.dto.portfolio.PortfolioZombieCandidate;
import com.marmitt.core.dto.portfolio.PortfolioZombieDetectionResult;
import com.marmitt.core.enums.BootAccountQueryPolicy;
import com.marmitt.core.enums.BootFailureMode;
import com.marmitt.core.enums.DlqReason;
import com.marmitt.core.enums.PortfolioReservationTtlStatus;
import com.marmitt.core.enums.PortfolioSanityStatus;
import com.marmitt.core.enums.PortfolioZombieDetectionStatus;
import com.marmitt.core.enums.RunnerStatus;
import com.marmitt.core.enums.TransactionStatus;
import com.marmitt.core.ports.inbound.boot.RunBootSequencePort;
import com.marmitt.core.ports.outbound.boot.BootExecutionObserverPort;
import com.marmitt.core.ports.outbound.exchange.rest.ExchangeAccountQueryPort;
import com.marmitt.core.ports.outbound.exchange.rest.ExchangeOrderQueryPort;
import com.marmitt.core.ports.outbound.repository.DeadLetterEntryRepositoryPort;
import com.marmitt.core.ports.outbound.repository.ExchangeAdapterRepositoryPort;
import com.marmitt.core.ports.outbound.repository.GlobalBalanceRepositoryPort;
import com.marmitt.core.ports.outbound.repository.PortfolioRepositoryPort;
import com.marmitt.core.ports.outbound.repository.StrategyRunnerRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Stream;

@Slf4j
@RequiredArgsConstructor
public class RunBootSequenceUseCase implements RunBootSequencePort {

    private static final BigDecimal DEFAULT_SANITY_THRESHOLD = new BigDecimal("0.00000001");
    private static final int MAX_ZOMBIE_LOG_SAMPLES = 10;
    private static final int MAX_TTL_SAMPLES = 10;
    private static final List<TransactionStatus> PENDING_STATUS = List.of(TransactionStatus.PENDING);

    private final PortfolioRepositoryPort portfolioRepository;
    private final StrategyRunnerRepositoryPort strategyRunnerRepository;
    private final ExchangeAdapterRepositoryPort exchangeAdapterRepository;
    private final GlobalBalanceRepositoryPort globalBalanceRepository;
    private final DeadLetterEntryRepositoryPort deadLetterEntryRepository;
    private final ConciliationOrderUpdateExecutor conciliationOrderUpdate;
    private final RunnerBootRecoveryUseCase runnerBootRecoveryUseCase;

    @Override
    public BootExecutionSummary execute(BootExecutionCommand command, BootExecutionObserverPort observer) {
        List<Portfolio> portfolios = portfolioRepository.findAll();
        List<StrategyRunner> eligibleRunners = portfolios.stream()
                .flatMap(this::loadRunnersByPortfolio)
                .filter(this::isEligibleForRecovery)
                .toList();

        executePhase("phase1.infrastructure", command.phase1Enabled(), observer,
                () -> runPhase1InfrastructureReadiness(observer, eligibleRunners));

        boolean phase2Enabled = command.phase2Enabled();
        executePhase("phase2.sanity",
                phase2Enabled && command.sanityEnabled(),
                observer,
                () -> runPhase2PortfolioSanity(command, observer, portfolios, eligibleRunners));
        executePhase("phase2.zombie",
                phase2Enabled && command.zombieDetectionEnabled(),
                observer,
                () -> runPhase2ZombieDetection(command, observer, portfolios, eligibleRunners));
        executePhase("phase2.reservation_ttl",
                phase2Enabled && command.reservationTtlEnabled(),
                observer,
                () -> runPhase2ReservationTtl(command, observer, portfolios, eligibleRunners));

        List<RunnerBootRecoveryUseCase.RecoverySummary> summaries = new ArrayList<>();
        executePhase("phase3.runner_recovery",
                command.phase3Enabled(),
                observer,
                () -> summaries.addAll(eligibleRunners.stream().map(this::recoverRunner).toList()));

        return new BootExecutionSummary(portfolios.size(), eligibleRunners.size(), List.copyOf(summaries));
    }

    private void runPhase1InfrastructureReadiness(BootExecutionObserverPort observer,
                                                  List<StrategyRunner> runners) {
        Set<String> exchanges = runners.stream()
                .map(StrategyRunner::getExchangeId)
                .filter(exchange -> exchange != null && !exchange.isBlank())
                .map(String::toUpperCase)
                .collect(java.util.stream.Collectors.toCollection(java.util.TreeSet::new));

        if (exchanges.isEmpty()) {
            log.info("bootOrchestrator.phase1: no exchange to validate");
            return;
        }

        log.info("bootOrchestrator.phase1: start exchanges={}", exchanges.size());

        for (String exchange : exchanges) {
            ExchangeBootReadiness readiness = exchangeAdapterRepository.findBootReadinessByName(exchange)
                    .orElseThrow(() -> new IllegalStateException(
                            "Boot readiness capability is not registered for exchange=" + exchange))
                    .checkBootReadiness();

            if (!readiness.ready()) {
                failFast(observer,
                        "phase1.infrastructure",
                        readiness.code(),
                        "Phase1 readiness failed exchange=" + exchange + " message=" + readiness.message());
            }

            log.info("bootOrchestrator.phase1: exchange={} ready code={} message={}",
                    exchange, readiness.code(), readiness.message());
        }

        log.info("bootOrchestrator.phase1: completed exchanges={}", exchanges.size());
    }

    private void runPhase2PortfolioSanity(BootExecutionCommand command,
                                          BootExecutionObserverPort observer,
                                          List<Portfolio> portfolios,
                                          List<StrategyRunner> eligibleRunners) {
        BootFailureMode mode = command.phase2Mode();
        log.info("bootOrchestrator.phase2.sanity: start portfolios={} mode={} accountQueryPolicy={} threshold={}",
                portfolios.size(), mode, command.accountQueryPolicy(), command.sanityThreshold());

        for (Portfolio portfolio : portfolios) {
            Set<String> exchanges = resolvePortfolioExchanges(portfolio, eligibleRunners);

            if (exchanges.isEmpty()) {
                log.debug("bootOrchestrator.phase2.sanity: portfolio={} skipped - no eligible runner exchange",
                        portfolio.getId());
                continue;
            }

            for (String exchange : exchanges) {
                long startedNs = System.nanoTime();
                PortfolioBootSanityResult result = executePortfolioBootSanity(
                        portfolio.getId(),
                        exchange,
                        command.sanityThreshold()
                );
                long durationMs = (System.nanoTime() - startedNs) / 1_000_000L;
                observer.onPortfolioPhaseEvaluated(
                        "phase2.sanity",
                        result.status().name(),
                        exchange,
                        mode.name(),
                        durationMs
                );

                switch (result.status()) {
                    case PASS, WARN_SURPLUS -> log.info(
                            "bootOrchestrator.phase2.sanity: portfolio={} exchange={} status={} code={} localTotal={} exchangeTotal={} signedDelta={} deviation={}",
                            result.portfolioId(),
                            result.exchangeId(),
                            result.status(),
                            result.code(),
                            result.localTotal(),
                            result.exchangeTotal(),
                            result.signedDelta(),
                            result.absoluteDeviation()
                    );
                    case SKIPPED -> log.warn(
                            "bootOrchestrator.phase2.sanity: portfolio={} exchange={} status={} code={} message={}",
                            result.portfolioId(),
                            result.exchangeId(),
                            result.status(),
                            result.code(),
                            result.message()
                    );
                    case FAIL_DEFICIT -> log.error(
                            "bootOrchestrator.phase2.sanity: portfolio={} exchange={} status={} code={} message={} localTotal={} exchangeTotal={} signedDelta={} deviation={}",
                            result.portfolioId(),
                            result.exchangeId(),
                            result.status(),
                            result.code(),
                            result.message(),
                            result.localTotal(),
                            result.exchangeTotal(),
                            result.signedDelta(),
                            result.absoluteDeviation()
                    );
                    case FAILED -> log.warn(
                            "bootOrchestrator.phase2.sanity: portfolio={} exchange={} status={} code={} message={}",
                            result.portfolioId(),
                            result.exchangeId(),
                            result.status(),
                            result.code(),
                            result.message()
                    );
                }

                boolean skippedWithFailPolicy = result.status() == PortfolioSanityStatus.SKIPPED
                        && command.accountQueryPolicy() == BootAccountQueryPolicy.FAIL;
                boolean criticalFailure = result.status() == PortfolioSanityStatus.FAIL_DEFICIT
                        || result.status() == PortfolioSanityStatus.FAILED;
                if ((skippedWithFailPolicy || criticalFailure) && mode == BootFailureMode.FAIL_FAST) {
                    failFast(observer,
                            "phase2.sanity",
                            result.code(),
                            "Phase2 sanity failed portfolio=" + result.portfolioId()
                                    + " exchange=" + result.exchangeId()
                                    + " message=" + result.message());
                }
            }
        }

        log.info("bootOrchestrator.phase2.sanity: completed");
    }

    private void runPhase2ZombieDetection(BootExecutionCommand command,
                                          BootExecutionObserverPort observer,
                                          List<Portfolio> portfolios,
                                          List<StrategyRunner> eligibleRunners) {
        BootFailureMode mode = command.phase2Mode();
        log.info("bootOrchestrator.phase2.zombie: start portfolios={} mode={} cutoffEnabled={}",
                portfolios.size(), mode, command.cutoffEnabled());

        for (Portfolio portfolio : portfolios) {
            Set<String> exchanges = resolvePortfolioExchanges(portfolio, eligibleRunners);
            if (exchanges.isEmpty()) {
                continue;
            }

            for (String exchange : exchanges) {
                long startedNs = System.nanoTime();
                PortfolioZombieDetectionResult result =
                        executePortfolioZombieDetection(
                                portfolio.getId(),
                                exchange,
                                command.cutoffEnabled()
                        );
                long durationMs = (System.nanoTime() - startedNs) / 1_000_000L;
                observer.onPortfolioPhaseEvaluated(
                        "phase2.zombie",
                        result.status().name(),
                        exchange,
                        mode.name(),
                        durationMs
                );

                switch (result.status()) {
                    case CLEAN -> log.info(
                            "bootOrchestrator.phase2.zombie: portfolio={} exchange={} status={} openOrders={} zombies={}",
                            result.portfolioId(),
                            result.exchangeId(),
                            result.status(),
                            result.openOrders(),
                            result.zombieCount()
                    );
                    case DETECTED -> {
                        boolean persistToDlq = mode == BootFailureMode.FAIL_FAST;
                        int persistedSamples = persistToDlq ? persistZombieSamplesToDlq(result) : 0;
                        log.warn(
                                "bootOrchestrator.phase2.zombie: portfolio={} exchange={} mode={} status={} code={} openOrders={} zombies={} invalidFormat={} unknownRunner={} noLocalMatch={} beforeCutoff={} unknownSymbol={} persistedSamples={} dlqPersisted={}",
                                result.portfolioId(),
                                result.exchangeId(),
                                mode,
                                result.status(),
                                result.code(),
                                result.openOrders(),
                                result.zombieCount(),
                                result.invalidFormatCount(),
                                result.unknownRunnerCount(),
                                result.noLocalMatchCount(),
                                result.beforeCutoffCount(),
                                result.unknownSymbolCount(),
                                persistedSamples,
                                persistToDlq
                        );
                        result.samples().forEach(sample -> log.warn(
                                "bootOrchestrator.phase2.zombie: sample portfolio={} exchange={} reason={} code={} clientOrderId={} exchangeOrderId={} symbol={}",
                                result.portfolioId(),
                                result.exchangeId(),
                                sample.reason(),
                                sample.code(),
                                sample.clientOrderId(),
                                sample.exchangeOrderId(),
                                sample.symbol()
                        ));
                    }
                    case SKIPPED -> log.warn(
                            "bootOrchestrator.phase2.zombie: portfolio={} exchange={} status={} code={} message={}",
                            result.portfolioId(),
                            result.exchangeId(),
                            result.status(),
                            result.code(),
                            result.message()
                    );
                    case FAILED -> log.error(
                            "bootOrchestrator.phase2.zombie: portfolio={} exchange={} status={} code={} message={}",
                            result.portfolioId(),
                            result.exchangeId(),
                            result.status(),
                            result.code(),
                            result.message()
                    );
                }

                boolean criticalFailure = result.status() == PortfolioZombieDetectionStatus.FAILED
                        || result.status() == PortfolioZombieDetectionStatus.DETECTED;
                if (criticalFailure && mode == BootFailureMode.FAIL_FAST) {
                    failFast(observer,
                            "phase2.zombie",
                            result.code(),
                            "Phase2 zombie detection failed portfolio=" + result.portfolioId()
                                    + " exchange=" + result.exchangeId()
                                    + " message=" + result.message());
                }
            }
        }

        log.info("bootOrchestrator.phase2.zombie: completed");
    }

    private void runPhase2ReservationTtl(BootExecutionCommand command,
                                         BootExecutionObserverPort observer,
                                         List<Portfolio> portfolios,
                                         List<StrategyRunner> eligibleRunners) {
        long ttlMs = command.reservationTtlMs();
        BootFailureMode mode = command.phase2Mode();
        log.info("bootOrchestrator.phase2.ttl: start portfolios={} mode={} ttlMs={}",
                portfolios.size(), mode, ttlMs);

        for (Portfolio portfolio : portfolios) {
            Set<String> exchanges = resolvePortfolioExchanges(portfolio, eligibleRunners);
            if (exchanges.isEmpty()) {
                continue;
            }

            for (String exchange : exchanges) {
                List<StrategyRunner> scopedRunners = eligibleRunners.stream()
                        .filter(runner -> runner.getPortfolioId().equals(portfolio.getId()))
                        .filter(runner -> exchange.equalsIgnoreCase(runner.getExchangeId()))
                        .toList();

                long startedNs = System.nanoTime();
                PortfolioReservationTtlResult result = executePortfolioReservationTtl(
                        portfolio.getId(),
                        exchange,
                        ttlMs,
                        scopedRunners
                );
                long durationMs = (System.nanoTime() - startedNs) / 1_000_000L;
                observer.onPortfolioPhaseEvaluated(
                        "phase2.reservation_ttl",
                        result.status().name(),
                        exchange,
                        mode.name(),
                        durationMs
                );

                switch (result.status()) {
                    case CLEAN -> log.info(
                            "bootOrchestrator.phase2.ttl: portfolio={} exchange={} status={} ttlMs={} scannedPending={} eligibleNoExchangeOrderId={} expired={} fresh={} errors={}",
                            result.portfolioId(),
                            result.exchangeId(),
                            result.status(),
                            result.ttlMs(),
                            result.scannedPendingCount(),
                            result.eligibleNoExchangeOrderIdCount(),
                            result.expiredCount(),
                            result.freshCount(),
                            result.errorCount()
                    );
                    case EXPIRED -> {
                        log.warn(
                                "bootOrchestrator.phase2.ttl: portfolio={} exchange={} status={} code={} ttlMs={} scannedPending={} eligibleNoExchangeOrderId={} expired={} fresh={} errors={}",
                                result.portfolioId(),
                                result.exchangeId(),
                                result.status(),
                                result.code(),
                                result.ttlMs(),
                                result.scannedPendingCount(),
                                result.eligibleNoExchangeOrderIdCount(),
                                result.expiredCount(),
                                result.freshCount(),
                                result.errorCount()
                        );
                        result.samples().forEach(transactionId -> log.warn(
                                "bootOrchestrator.phase2.ttl: expiredSample portfolio={} exchange={} transactionId={}",
                                result.portfolioId(),
                                result.exchangeId(),
                                transactionId
                        ));
                    }
                    case SKIPPED -> log.warn(
                            "bootOrchestrator.phase2.ttl: portfolio={} exchange={} status={} code={} message={} ttlMs={}",
                            result.portfolioId(),
                            result.exchangeId(),
                            result.status(),
                            result.code(),
                            result.message(),
                            result.ttlMs()
                    );
                    case FAILED -> log.error(
                            "bootOrchestrator.phase2.ttl: portfolio={} exchange={} status={} code={} message={} ttlMs={} scannedPending={} eligibleNoExchangeOrderId={} expired={} fresh={} errors={}",
                            result.portfolioId(),
                            result.exchangeId(),
                            result.status(),
                            result.code(),
                            result.message(),
                            result.ttlMs(),
                            result.scannedPendingCount(),
                            result.eligibleNoExchangeOrderIdCount(),
                            result.expiredCount(),
                            result.freshCount(),
                            result.errorCount()
                    );
                }

                boolean criticalFailure = result.status() == PortfolioReservationTtlStatus.FAILED;
                if (criticalFailure && mode == BootFailureMode.FAIL_FAST) {
                    failFast(observer,
                            "phase2.reservation_ttl",
                            result.code(),
                            "Phase2 reservation TTL failed portfolio=" + result.portfolioId()
                                    + " exchange=" + result.exchangeId()
                                    + " message=" + result.message());
                }
            }
        }

        log.info("bootOrchestrator.phase2.ttl: completed");
    }

    private PortfolioBootSanityResult executePortfolioBootSanity(UUID portfolioId,
                                                                 String exchangeId,
                                                                 BigDecimal threshold) {
        Optional<GlobalBalance> balanceOptional = globalBalanceRepository.findByPortfolioId(portfolioId);
        if (balanceOptional.isEmpty()) {
            return PortfolioBootSanityResult.failed(
                    portfolioId, exchangeId, "GLOBAL_BALANCE_NOT_FOUND",
                    "GlobalBalance not found for portfolio.");
        }

        Optional<ExchangeAccountQueryPort> accountQuery =
                exchangeAdapterRepository.findAccountQueryByName(exchangeId);
        if (accountQuery.isEmpty()) {
            return PortfolioBootSanityResult.skipped(
                    portfolioId, exchangeId, "ACCOUNT_QUERY_NOT_AVAILABLE",
                    "Exchange account query capability is not available.");
        }

        try {
            GlobalBalance local = balanceOptional.get();
            AccountDataDto account = accountQuery.get().queryAccountSnapshot();

            String baseCurrency = local.getBaseCurrency().toUpperCase();
            BigDecimal localTotal = local.getTotalBalance();
            BigDecimal exchangeTotal = readTotalForCurrency(account, baseCurrency);
            BigDecimal signedDelta = exchangeTotal.subtract(localTotal);
            BigDecimal absoluteDeviation = signedDelta.abs();
            BigDecimal effectiveThreshold = threshold != null ? threshold : DEFAULT_SANITY_THRESHOLD;

            if (absoluteDeviation.compareTo(effectiveThreshold) <= 0) {
                return PortfolioBootSanityResult.pass(
                        portfolioId, exchangeId, baseCurrency, localTotal, exchangeTotal, signedDelta, absoluteDeviation);
            }

            if (signedDelta.compareTo(BigDecimal.ZERO) > 0) {
                return PortfolioBootSanityResult.warnSurplus(
                        portfolioId, exchangeId, baseCurrency, localTotal, exchangeTotal, signedDelta, absoluteDeviation);
            }

            return PortfolioBootSanityResult.failDeficit(
                    portfolioId, exchangeId, baseCurrency, localTotal, exchangeTotal, signedDelta, absoluteDeviation);
        } catch (UnsupportedOperationException e) {
            return PortfolioBootSanityResult.skipped(
                    portfolioId, exchangeId, "ACCOUNT_QUERY_UNSUPPORTED", e.getMessage());
        } catch (Exception e) {
            log.warn("portfolioBootSanity: failed portfolioId={} exchange={} reason={}",
                    portfolioId, exchangeId, e.getMessage());
            return PortfolioBootSanityResult.failed(
                    portfolioId, exchangeId, "ACCOUNT_QUERY_FAILED", e.getMessage());
        }
    }

    private PortfolioZombieDetectionResult executePortfolioZombieDetection(UUID portfolioId,
                                                                           String exchangeId,
                                                                           boolean cutoffEnabled) {
        List<StrategyRunner> scopedRunners = strategyRunnerRepository.findByPortfolioId(portfolioId).stream()
                .filter(runner -> runner.getStatus() != RunnerStatus.ARCHIVED)
                .filter(runner -> runner.getExchangeId() != null
                        && exchangeId != null
                        && exchangeId.equalsIgnoreCase(runner.getExchangeId()))
                .toList();

        if (scopedRunners.isEmpty()) {
            return PortfolioZombieDetectionResult.skipped(
                    portfolioId,
                    exchangeId,
                    "NO_RUNNERS",
                    "No eligible runners found for portfolio/exchange."
            );
        }

        Optional<ExchangeOrderQueryPort> orderQueryOptional =
                exchangeAdapterRepository.findOrderQueryByName(exchangeId);
        if (orderQueryOptional.isEmpty()) {
            return PortfolioZombieDetectionResult.skipped(
                    portfolioId, exchangeId, "ORDER_QUERY_NOT_AVAILABLE",
                    "Exchange order query capability is not available.");
        }

        try {
            List<OrderDataDto> openOrders = orderQueryOptional.get().listAllOpenOrders();
            return classifyOpenOrders(
                    portfolioId,
                    exchangeId,
                    openOrders != null ? openOrders : List.of(),
                    scopedRunners,
                    cutoffEnabled
            );
        } catch (UnsupportedOperationException e) {
            return PortfolioZombieDetectionResult.skipped(
                    portfolioId, exchangeId, "ORDER_QUERY_UNSUPPORTED", e.getMessage());
        } catch (Exception e) {
            log.warn("portfolioZombieDetection: failed portfolioId={} exchange={} reason={}",
                    portfolioId, exchangeId, e.getMessage());
            return PortfolioZombieDetectionResult.failed(
                    portfolioId, exchangeId, "ORDER_QUERY_FAILED", e.getMessage());
        }
    }

    private PortfolioZombieDetectionResult classifyOpenOrders(UUID portfolioId,
                                                              String exchangeId,
                                                              List<OrderDataDto> openOrders,
                                                              List<StrategyRunner> scopedRunners,
                                                              boolean cutoffEnabled) {
        Map<UUID, StrategyRunner> scopedRunnerById = scopedRunners.stream()
                .collect(java.util.stream.Collectors.toMap(StrategyRunner::getId, Function.identity()));
        Map<String, StrategyRunner> scopedRunnerByShortCode = scopedRunners.stream()
                .collect(java.util.stream.Collectors.toMap(
                        runner -> runner.getShortCode().toLowerCase(Locale.ROOT),
                        Function.identity(),
                        (left, right) -> left
                ));
        Set<UUID> scopedRunnerIds = scopedRunnerById.keySet();

        int invalidFormatCount = 0;
        int unknownRunnerCount = 0;
        int noLocalMatchCount = 0;
        int beforeCutoffCount = 0;
        int unknownSymbolCount = 0;
        List<PortfolioZombieCandidate> samples = new ArrayList<>();
        Instant portfolioCutoff = cutoffEnabled ? resolvePortfolioCutoff(scopedRunners) : null;

        for (OrderDataDto order : openOrders) {
            String clientOrderId = order.clientOrderId();
            Optional<Transaction> transactionOptional = (clientOrderId == null || clientOrderId.isBlank())
                    ? Optional.empty()
                    : strategyRunnerRepository.findTransactionByClientOrderId(clientOrderId);
            String shortCode = ClientOrderId.getRunnerShortCode(clientOrderId);
            if (shortCode != null) {
                shortCode = shortCode.toLowerCase(Locale.ROOT);
            }
            final String normalizedShortCode = shortCode;

            boolean belongsToCurrentPortfolio = transactionOptional
                    .map(tx -> scopedRunnerIds.contains(tx.getRunnerId()))
                    .orElseGet(() -> normalizedShortCode != null
                            && scopedRunnerByShortCode.containsKey(normalizedShortCode));

            if (!belongsToCurrentPortfolio) {
                continue;
            }

            if (cutoffEnabled && isBeforeCutoff(order.timestamp(), portfolioCutoff)) {
                beforeCutoffCount++;
                addZombieSample(samples, order, DlqReason.RECONCILIATION_CONFLICT, "BEFORE_CUTOFF");
                continue;
            }

            if (shortCode == null) {
                invalidFormatCount++;
                addZombieSample(samples, order, DlqReason.INVALID_FORMAT, "INVALID_FORMAT");
                continue;
            }

            if (transactionOptional.isEmpty()) {
                noLocalMatchCount++;
                addZombieSample(samples, order, DlqReason.RECONCILIATION_CONFLICT, "NO_LOCAL_MATCH");
                continue;
            }

            StrategyRunner runnerByShortCode = scopedRunnerByShortCode.get(shortCode);
            if (runnerByShortCode == null || runnerByShortCode.getStatus() == RunnerStatus.ARCHIVED) {
                unknownRunnerCount++;
                addZombieSample(samples, order, DlqReason.UNKNOWN_RUNNER, "RUNNER_NOT_FOUND");
                continue;
            }

            StrategyRunner runner = scopedRunnerById.get(transactionOptional.get().getRunnerId());
            if (runner == null || runner.getStatus() == RunnerStatus.ARCHIVED) {
                unknownRunnerCount++;
                addZombieSample(samples, order, DlqReason.UNKNOWN_RUNNER, "RUNNER_NOT_FOUND");
                continue;
            }

            if (cutoffEnabled && isBeforeCutoff(order.timestamp(), resolveRunnerCutoff(runner))) {
                beforeCutoffCount++;
                addZombieSample(samples, order, DlqReason.RECONCILIATION_CONFLICT, "BEFORE_CUTOFF");
                continue;
            }

            if (!transactionOptional.get().getRunnerId().equals(runnerByShortCode.getId())) {
                unknownRunnerCount++;
                addZombieSample(samples, order, DlqReason.UNKNOWN_RUNNER, "OWNER_MISMATCH");
                continue;
            }

            if (!isOrderSymbolCompatible(order.symbol(), runner.getSymbol())) {
                unknownSymbolCount++;
                addZombieSample(samples, order, DlqReason.UNKNOWN_SYMBOL, "SYMBOL_MISMATCH");
            }
        }

        if (invalidFormatCount == 0
                && unknownRunnerCount == 0
                && noLocalMatchCount == 0
                && beforeCutoffCount == 0
                && unknownSymbolCount == 0) {
            return PortfolioZombieDetectionResult.clean(portfolioId, exchangeId, openOrders.size());
        }

        return PortfolioZombieDetectionResult.detected(
                portfolioId,
                exchangeId,
                openOrders.size(),
                invalidFormatCount,
                unknownRunnerCount,
                noLocalMatchCount,
                beforeCutoffCount,
                unknownSymbolCount,
                samples
        );
    }

    private PortfolioReservationTtlResult executePortfolioReservationTtl(UUID portfolioId,
                                                                         String exchangeId,
                                                                         long ttlMs,
                                                                         List<StrategyRunner> scopedRunners) {
        if (ttlMs <= 0) {
            return PortfolioReservationTtlResult.skipped(
                    portfolioId, exchangeId, ttlMs, "TTL_DISABLED",
                    "Reservation TTL is disabled (ttlMs <= 0).");
        }
        if (scopedRunners == null || scopedRunners.isEmpty()) {
            return PortfolioReservationTtlResult.skipped(
                    portfolioId, exchangeId, ttlMs, "NO_RUNNERS",
                    "No eligible runners found for portfolio/exchange.");
        }

        Instant cutoff = Instant.now().minusMillis(ttlMs);
        int scannedPendingCount = 0;
        int eligibleNoExchangeOrderIdCount = 0;
        int expiredCount = 0;
        int freshCount = 0;
        int errorCount = 0;
        List<UUID> samples = new ArrayList<>();

        for (StrategyRunner runner : scopedRunners) {
            List<Transaction> pending = strategyRunnerRepository
                    .findByRunnerIdAndStatuses(runner.getId(), PENDING_STATUS);
            scannedPendingCount += pending.size();

            for (Transaction tx : pending) {
                if (hasExchangeOrderId(tx)) {
                    continue;
                }
                eligibleNoExchangeOrderIdCount++;

                if (tx.getRequestedAt() != null && tx.getRequestedAt().isAfter(cutoff)) {
                    freshCount++;
                    continue;
                }

                try {
                    conciliationOrderUpdate.execute(buildSyntheticTtlExpired(tx));
                    expiredCount++;
                    if (samples.size() < MAX_TTL_SAMPLES) {
                        samples.add(tx.getId());
                    }
                } catch (Exception e) {
                    errorCount++;
                    log.error(
                            "reservationTtl: failed expiring transactionId={} runnerId={} portfolioId={} exchange={} reason={}",
                            tx.getId(),
                            tx.getRunnerId(),
                            portfolioId,
                            exchangeId,
                            e.getMessage(),
                            e
                    );
                }
            }
        }

        if (errorCount > 0) {
            return PortfolioReservationTtlResult.failed(
                    portfolioId,
                    exchangeId,
                    ttlMs,
                    "TTL_PARTIAL_FAILURE",
                    "One or more transactions failed to expire during boot reservation TTL cleanup.",
                    scannedPendingCount,
                    eligibleNoExchangeOrderIdCount,
                    expiredCount,
                    freshCount,
                    errorCount,
                    samples
            );
        }

        if (expiredCount > 0) {
            return PortfolioReservationTtlResult.expired(
                    portfolioId,
                    exchangeId,
                    ttlMs,
                    scannedPendingCount,
                    eligibleNoExchangeOrderIdCount,
                    expiredCount,
                    freshCount,
                    samples
            );
        }

        return PortfolioReservationTtlResult.clean(
                portfolioId,
                exchangeId,
                ttlMs,
                scannedPendingCount,
                eligibleNoExchangeOrderIdCount,
                freshCount
        );
    }

    private static BigDecimal readTotalForCurrency(AccountDataDto account, String currency) {
        Map<String, BigDecimal> available = account.balances();
        Map<String, BigDecimal> locked = account.lockedBalances();
        BigDecimal exchangeAvailable = available != null
                ? available.getOrDefault(currency, BigDecimal.ZERO)
                : BigDecimal.ZERO;
        BigDecimal exchangeLocked = locked != null
                ? locked.getOrDefault(currency, BigDecimal.ZERO)
                : BigDecimal.ZERO;
        return exchangeAvailable.add(exchangeLocked);
    }

    private static void addZombieSample(List<PortfolioZombieCandidate> samples,
                                        OrderDataDto order,
                                        DlqReason reason,
                                        String code) {
        if (samples.size() >= MAX_ZOMBIE_LOG_SAMPLES) {
            return;
        }
        samples.add(new PortfolioZombieCandidate(
                order.clientOrderId(),
                order.orderId(),
                order.symbol() != null ? order.symbol().toString() : null,
                reason,
                code
        ));
    }

    private static Instant resolvePortfolioCutoff(List<StrategyRunner> scopedRunners) {
        return scopedRunners.stream()
                .map(RunBootSequenceUseCase::resolveRunnerCutoff)
                .min(Comparator.naturalOrder())
                .orElse(null);
    }

    private static Instant resolveRunnerCutoff(StrategyRunner runner) {
        if (runner.getLastReconciliationAt() != null) {
            return runner.getLastReconciliationAt();
        }
        return runner.getCreatedAt();
    }

    private static boolean isBeforeCutoff(Instant orderTimestamp, Instant cutoff) {
        return cutoff != null && orderTimestamp != null && orderTimestamp.isBefore(cutoff);
    }

    private static boolean isOrderSymbolCompatible(Symbol orderSymbol, String runnerSymbol) {
        if (runnerSymbol == null || runnerSymbol.isBlank() || orderSymbol == null) {
            return true;
        }
        return runnerSymbol.equalsIgnoreCase(orderSymbol.toString());
    }

    private static boolean hasExchangeOrderId(Transaction tx) {
        String exchangeOrderId = tx.getExchangeOrderId();
        return exchangeOrderId != null && !exchangeOrderId.isBlank();
    }

    private static OrderDataDto buildSyntheticTtlExpired(Transaction tx) {
        return new OrderDataDto(
                tx.getExchangeOrderId() != null ? tx.getExchangeOrderId() : "BOOT_TTL_" + tx.getId(),
                tx.getClientOrderId(),
                Symbol.of(tx.getSymbol()),
                tx.isBuy() ? OrderDataDto.OrderSide.BUY : OrderDataDto.OrderSide.SELL,
                OrderDataDto.OrderType.LIMIT,
                tx.getQuantity(),
                tx.getEffectiveExecutedQuantity(),
                tx.getPrice(),
                tx.getEffectiveExecutedPrice(),
                BigDecimal.ZERO,
                OrderDataDto.OrderStatus.EXPIRED,
                "BOOT_TTL_EXPIRED",
                Instant.now()
        );
    }

    private Set<String> resolvePortfolioExchanges(Portfolio portfolio, List<StrategyRunner> eligibleRunners) {
        return eligibleRunners.stream()
                .filter(runner -> runner.getPortfolioId().equals(portfolio.getId()))
                .map(StrategyRunner::getExchangeId)
                .filter(exchange -> exchange != null && !exchange.isBlank())
                .map(String::toUpperCase)
                .collect(java.util.stream.Collectors.toCollection(java.util.TreeSet::new));
    }

    private Stream<StrategyRunner> loadRunnersByPortfolio(Portfolio portfolio) {
        return strategyRunnerRepository.findByPortfolioId(portfolio.getId()).stream();
    }

    private boolean isEligibleForRecovery(StrategyRunner runner) {
        return runner.getStatus() != RunnerStatus.ARCHIVED
                && runner.getStatus() != RunnerStatus.TERMINATING;
    }

    private RunnerBootRecoveryUseCase.RecoverySummary recoverRunner(StrategyRunner runner) {
        RunnerBootRecoveryUseCase.RecoverySummary summary = runnerBootRecoveryUseCase.recoverRunner(runner);

        log.info("bootOrchestrator: runner={} inFlight={} zombies={} limbo={}",
                summary.runnerId(), summary.inFlightCount(), summary.zombiesCount(), summary.limboCount());

        if (log.isDebugEnabled()) {
            summary.notes().forEach(note ->
                    log.debug("bootOrchestrator: runner={} plan={}", summary.runnerId(), note));
        }

        return summary;
    }

    private void executePhase(String phase, boolean enabled, BootExecutionObserverPort observer, Runnable action) {
        if (!enabled) {
            observer.onPhaseSkipped(phase, "disabled_by_configuration");
            log.info("bootOrchestrator: phase={} status=SKIPPED reason=disabled_by_configuration", phase);
            return;
        }

        long startedNs = System.nanoTime();
        observer.onPhaseStarted(phase);
        try {
            action.run();
            long durationMs = (System.nanoTime() - startedNs) / 1_000_000L;
            observer.onPhaseCompleted(phase, true, durationMs, "ok");
            log.info("bootOrchestrator: phase={} status=SUCCESS durationMs={}", phase, durationMs);
        } catch (RuntimeException e) {
            long durationMs = (System.nanoTime() - startedNs) / 1_000_000L;
            observer.onPhaseCompleted(phase, false, durationMs, e.getMessage());
            if (e instanceof BootFailFastException) {
                throw e;
            }
            throw new BootPhaseExecutionException(phase, e.getMessage(), e);
        }
    }

    private void failFast(BootExecutionObserverPort observer, String phase, String code, String message) {
        observer.onFailFastRequested(phase, code, message);
        throw new BootFailFastException(phase, code, message);
    }

    private int persistZombieSamplesToDlq(PortfolioZombieDetectionResult result) {
        int persisted = 0;
        for (PortfolioZombieCandidate sample : result.samples()) {
            UUID runnerId = resolveRunnerIdForZombieSample(result.portfolioId(), sample);

            boolean alreadyOpen = deadLetterEntryRepository.existsUnresolvedByIdentity(
                    result.portfolioId(),
                    runnerId,
                    sample.clientOrderId(),
                    sample.exchangeOrderId(),
                    sample.reason()
            );

            if (alreadyOpen) {
                continue;
            }

            DeadLetterEntry entry = new DeadLetterEntry(
                    result.portfolioId(),
                    runnerId,
                    sample.clientOrderId(),
                    sample.exchangeOrderId(),
                    "source=boot.phase2.zombie"
                            + ", exchange=" + result.exchangeId()
                            + ", runnerId=" + runnerId
                            + ", code=" + sample.code()
                            + ", symbol=" + sample.symbol()
                            + ", reason=" + sample.reason(),
                    sample.reason()
            );
            deadLetterEntryRepository.save(entry);
            persisted++;
        }
        return persisted;
    }

    private UUID resolveRunnerIdForZombieSample(UUID portfolioId, PortfolioZombieCandidate sample) {
        String clientOrderId = sample.clientOrderId();
        if (clientOrderId == null || clientOrderId.isBlank()) {
            return null;
        }

        var byTransaction = strategyRunnerRepository.findTransactionByClientOrderId(clientOrderId)
                .flatMap(tx -> strategyRunnerRepository.findById(tx.getRunnerId())
                        .filter(runner -> portfolioId.equals(runner.getPortfolioId()))
                        .map(StrategyRunner::getId));
        if (byTransaction.isPresent()) {
            return byTransaction.get();
        }

        String shortCode = ClientOrderId.getRunnerShortCode(clientOrderId);
        if (shortCode == null || shortCode.isBlank()) {
            return null;
        }

        return strategyRunnerRepository.findByShortCodeAndPortfolioId(shortCode.toLowerCase(Locale.ROOT), portfolioId)
                .map(StrategyRunner::getId)
                .orElse(null);
    }
}
