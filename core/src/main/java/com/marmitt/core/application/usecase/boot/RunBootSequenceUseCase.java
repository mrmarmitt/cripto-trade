package com.marmitt.core.application.usecase.boot;

import com.marmitt.core.application.usecase.portfolio.PortfolioBootSanityUseCase;
import com.marmitt.core.application.usecase.portfolio.PortfolioReservationTtlUseCase;
import com.marmitt.core.application.usecase.portfolio.PortfolioZombieDetectionUseCase;
import com.marmitt.core.application.usecase.runner.RunnerBootRecoveryUseCase;
import com.marmitt.core.domain.portfolio.DeadLetterEntry;
import com.marmitt.core.domain.portfolio.Portfolio;
import com.marmitt.core.domain.runner.ClientOrderId;
import com.marmitt.core.domain.runner.StrategyRunner;
import com.marmitt.core.dto.boot.BootExecutionCommand;
import com.marmitt.core.dto.boot.BootExecutionSummary;
import com.marmitt.core.dto.exchange.boot.ExchangeBootReadiness;
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
import com.marmitt.core.ports.outbound.boot.BootExecutionObserverPort;
import com.marmitt.core.ports.outbound.repository.DeadLetterEntryRepositoryPort;
import com.marmitt.core.ports.outbound.repository.ExchangeAdapterRepositoryPort;
import com.marmitt.core.ports.outbound.repository.PortfolioRepositoryPort;
import com.marmitt.core.ports.outbound.repository.StrategyRunnerRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

@Slf4j
@RequiredArgsConstructor
public class RunBootSequenceUseCase {

    private final PortfolioRepositoryPort portfolioRepository;
    private final StrategyRunnerRepositoryPort strategyRunnerRepository;
    private final ExchangeAdapterRepositoryPort exchangeAdapterRepository;
    private final PortfolioBootSanityUseCase portfolioBootSanityUseCase;
    private final PortfolioReservationTtlUseCase portfolioReservationTtlUseCase;
    private final PortfolioZombieDetectionUseCase portfolioZombieDetectionUseCase;
    private final DeadLetterEntryRepositoryPort deadLetterEntryRepository;
    private final RunnerBootRecoveryUseCase runnerBootRecoveryUseCase;

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
                PortfolioBootSanityResult result = portfolioBootSanityUseCase.execute(
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
                        portfolioZombieDetectionUseCase.execute(
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
                PortfolioReservationTtlResult result = portfolioReservationTtlUseCase.execute(
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
