package com.marmitt.core.application.usecase.boot;

import com.marmitt.core.application.usecase.boot.phase2.PortfolioBootSanityUseCase;
import com.marmitt.core.application.usecase.boot.phase2.PortfolioReservationTtlUseCase;
import com.marmitt.core.application.usecase.boot.phase2.PortfolioZombieDetectionUseCase;
import com.marmitt.core.application.usecase.runner.RunnerBootRecoveryUseCase;
import com.marmitt.core.domain.portfolio.DeadLetterEntry;
import com.marmitt.core.domain.portfolio.Portfolio;
import com.marmitt.core.domain.runner.ClientOrderId;
import com.marmitt.core.domain.runner.StrategyRunner;
import com.marmitt.core.dto.boot.BootExecutionCommand;
import com.marmitt.core.dto.exchange.boot.ExchangeBootReadiness;
import com.marmitt.core.dto.portfolio.PortfolioBootSanityResult;
import com.marmitt.core.dto.portfolio.PortfolioReservationTtlResult;
import com.marmitt.core.dto.portfolio.PortfolioZombieCandidate;
import com.marmitt.core.dto.portfolio.PortfolioZombieDetectionResult;
import com.marmitt.core.enums.BootAccountQueryPolicy;
import com.marmitt.core.enums.BootFailureMode;
import com.marmitt.core.enums.PortfolioReservationTtlStatus;
import com.marmitt.core.enums.PortfolioSanityStatus;
import com.marmitt.core.enums.PortfolioZombieDetectionStatus;
import com.marmitt.core.enums.RunnerStatus;
import com.marmitt.core.ports.inbound.boot.RunBootSequencePort;
import com.marmitt.core.ports.outbound.boot.BootExecutionObserverPort;
import com.marmitt.core.ports.outbound.repository.DeadLetterEntryRepositoryPort;
import com.marmitt.core.ports.outbound.repository.ExchangeAdapterRepositoryPort;
import com.marmitt.core.ports.outbound.repository.PortfolioRepositoryPort;
import com.marmitt.core.ports.outbound.repository.StrategyRunnerRepositoryPort;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
public class RunBootSequenceUseCase implements RunBootSequencePort {

    private final PortfolioRepositoryPort portfolioRepository;
    private final StrategyRunnerRepositoryPort strategyRunnerRepository;
    private final ExchangeAdapterRepositoryPort exchangeAdapterRepository;
    private final PortfolioBootSanityUseCase portfolioBootSanityUseCase;
    private final PortfolioReservationTtlUseCase portfolioReservationTtlUseCase;
    private final PortfolioZombieDetectionUseCase portfolioZombieDetectionUseCase;
    private final DeadLetterEntryRepositoryPort deadLetterEntryRepository;
    private final RunnerBootRecoveryUseCase runnerBootRecoveryUseCase;

    public RunBootSequenceUseCase(PortfolioRepositoryPort portfolioRepository,
                                  StrategyRunnerRepositoryPort strategyRunnerRepository,
                                  ExchangeAdapterRepositoryPort exchangeAdapterRepository,
                                  PortfolioBootSanityUseCase portfolioBootSanityUseCase,
                                  PortfolioReservationTtlUseCase portfolioReservationTtlUseCase,
                                  PortfolioZombieDetectionUseCase portfolioZombieDetectionUseCase,
                                  DeadLetterEntryRepositoryPort deadLetterEntryRepository,
                                  RunnerBootRecoveryUseCase runnerBootRecoveryUseCase) {
        this.portfolioRepository = portfolioRepository;
        this.strategyRunnerRepository = strategyRunnerRepository;
        this.exchangeAdapterRepository = exchangeAdapterRepository;
        this.portfolioBootSanityUseCase = portfolioBootSanityUseCase;
        this.portfolioReservationTtlUseCase = portfolioReservationTtlUseCase;
        this.portfolioZombieDetectionUseCase = portfolioZombieDetectionUseCase;
        this.deadLetterEntryRepository = deadLetterEntryRepository;
        this.runnerBootRecoveryUseCase = runnerBootRecoveryUseCase;
    }

    @Override
    public void execute(BootExecutionCommand command, BootExecutionObserverPort observer) {
        String runId = UUID.randomUUID().toString();
        observer.onRunStarted(runId, command.failureMode().name());
        log.info("bootSequence: start runId={} mode={} phase1Enabled={} phase2Enabled={} phase3Enabled={}",
                runId, command.failureMode(), command.phase1Enabled(), command.phase2Enabled(), command.phase3Enabled());

        try {
            List<Portfolio> portfolios = portfolioRepository.findAll();
            List<StrategyRunner> eligibleRunners = portfolios.stream()
                    .flatMap(this::loadRunnersByPortfolio)
                    .filter(this::isEligibleForRecovery)
                    .toList();

            executePhase("phase1.infrastructure", command.phase1Enabled(),
                    () -> runPhase1InfrastructureReadiness(runId, eligibleRunners, observer),
                    observer);

            boolean phase2Enabled = command.phase2Enabled();
            executePhase("phase2.sanity", phase2Enabled && command.sanityEnabled(),
                    () -> runPhase2PortfolioSanity(runId, portfolios, eligibleRunners, command, observer),
                    observer);
            executePhase("phase2.zombie", phase2Enabled && command.zombieEnabled(),
                    () -> runPhase2ZombieDetection(runId, portfolios, eligibleRunners, command, observer),
                    observer);
            executePhase("phase2.reservation_ttl", phase2Enabled && command.ttlEnabled(),
                    () -> runPhase2ReservationTtl(runId, portfolios, eligibleRunners, command, observer),
                    observer);

            List<RunnerBootRecoveryUseCase.RecoverySummary> summaries = new ArrayList<>();
            executePhase("phase3.runner_recovery", command.phase3Enabled(),
                    () -> summaries.addAll(eligibleRunners.stream().map(this::recoverRunner).toList()),
                    observer);

            observer.onRunCompleted(runId);
            log.info("bootSequence: completed runId={} portfolios={} runners={}",
                    runId, portfolios.size(), summaries.size());
        } catch (RuntimeException e) {
            observer.onRunFailed(runId, null, e.getMessage());
            throw e;
        }
    }

    private void runPhase1InfrastructureReadiness(String runId,
                                                  List<StrategyRunner> runners,
                                                  BootExecutionObserverPort observer) {
        Set<String> exchanges = runners.stream()
                .map(StrategyRunner::getExchangeId)
                .filter(exchange -> exchange != null && !exchange.isBlank())
                .map(String::toUpperCase)
                .collect(Collectors.toCollection(java.util.TreeSet::new));

        if (exchanges.isEmpty()) {
            log.info("bootSequence.phase1: no exchange to validate");
            return;
        }

        log.info("bootSequence.phase1: start exchanges={}", exchanges.size());

        for (String exchange : exchanges) {
            ExchangeBootReadiness readiness = exchangeAdapterRepository.findAdapter(exchange)
                    .orElseThrow(() -> new IllegalStateException(
                            "Boot readiness capability is not registered for exchange=" + exchange))
                    .bootReadiness().checkBootReadiness();

            if (!readiness.ready()) {
                throw triggerFailFast(runId, "phase1.infrastructure", readiness.code(),
                        "Phase1 readiness failed exchange=" + exchange + " message=" + readiness.message(),
                        observer);
            }

            log.info("bootSequence.phase1: exchange={} ready code={} message={}",
                    exchange, readiness.code(), readiness.message());
        }

        log.info("bootSequence.phase1: completed runId={} exchanges={}", runId, exchanges.size());
    }

    private void runPhase2PortfolioSanity(String runId,
                                          List<Portfolio> portfolios,
                                          List<StrategyRunner> eligibleRunners,
                                          BootExecutionCommand command,
                                          BootExecutionObserverPort observer) {
        BootFailureMode mode = command.failureMode();
        log.info("bootSequence.phase2.sanity: start portfolios={} mode={} accountQueryPolicy={} threshold={}",
                portfolios.size(), mode, command.accountQueryPolicy(), command.sanityThreshold());

        for (Portfolio portfolio : portfolios) {
            Set<String> exchanges = resolvePortfolioExchanges(portfolio, eligibleRunners);

            if (exchanges.isEmpty()) {
                log.debug("bootSequence.phase2.sanity: portfolio={} skipped - no eligible runner exchange",
                        portfolio.getId());
                continue;
            }

            for (String exchange : exchanges) {
                long startedNs = System.nanoTime();
                PortfolioBootSanityResult result =
                        portfolioBootSanityUseCase.execute(portfolio.getId(), exchange, command.sanityThreshold());
                long durationMs = (System.nanoTime() - startedNs) / 1_000_000L;
                observer.onPortfolioPhaseEvaluated("phase2.sanity", result.status().name(), exchange, mode.name(), durationMs);

                switch (result.status()) {
                    case PASS, WARN_SURPLUS -> log.info(
                            "bootSequence.phase2.sanity: portfolio={} exchange={} status={} code={} localTotal={} exchangeTotal={} signedDelta={} deviation={}",
                            result.portfolioId(), result.exchangeId(), result.status(), result.code(),
                            result.localTotal(), result.exchangeTotal(), result.signedDelta(), result.absoluteDeviation());
                    case SKIPPED -> log.warn(
                            "bootSequence.phase2.sanity: portfolio={} exchange={} status={} code={} message={}",
                            result.portfolioId(), result.exchangeId(), result.status(), result.code(), result.message());
                    case FAIL_DEFICIT -> log.error(
                            "bootSequence.phase2.sanity: portfolio={} exchange={} status={} code={} message={} localTotal={} exchangeTotal={} signedDelta={} deviation={}",
                            result.portfolioId(), result.exchangeId(), result.status(), result.code(), result.message(),
                            result.localTotal(), result.exchangeTotal(), result.signedDelta(), result.absoluteDeviation());
                    case FAILED -> log.warn(
                            "bootSequence.phase2.sanity: portfolio={} exchange={} status={} code={} message={}",
                            result.portfolioId(), result.exchangeId(), result.status(), result.code(), result.message());
                }

                boolean skippedWithFailPolicy = result.status() == PortfolioSanityStatus.SKIPPED
                        && command.accountQueryPolicy() == BootAccountQueryPolicy.FAIL;
                boolean criticalFailure = result.status() == PortfolioSanityStatus.FAIL_DEFICIT
                        || result.status() == PortfolioSanityStatus.FAILED;
                if ((skippedWithFailPolicy || criticalFailure) && mode == BootFailureMode.FAIL_FAST) {
                    throw triggerFailFast(runId, "phase2.sanity", result.code(),
                            "Phase2 sanity failed portfolio=" + result.portfolioId()
                                    + " exchange=" + result.exchangeId()
                                    + " message=" + result.message(),
                            observer);
                }
            }
        }

        log.info("bootSequence.phase2.sanity: completed runId={} portfolios={}", runId, portfolios.size());
    }

    private void runPhase2ZombieDetection(String runId,
                                          List<Portfolio> portfolios,
                                          List<StrategyRunner> eligibleRunners,
                                          BootExecutionCommand command,
                                          BootExecutionObserverPort observer) {
        BootFailureMode mode = command.failureMode();
        log.info("bootSequence.phase2.zombie: start portfolios={} mode={} cutoffEnabled={}",
                portfolios.size(), mode, command.cutoffEnabled());

        int zombiesTotal = 0;
        for (Portfolio portfolio : portfolios) {
            Set<String> exchanges = resolvePortfolioExchanges(portfolio, eligibleRunners);
            if (exchanges.isEmpty()) {
                continue;
            }

            for (String exchange : exchanges) {
                long startedNs = System.nanoTime();
                PortfolioZombieDetectionResult result =
                        portfolioZombieDetectionUseCase.execute(portfolio.getId(), exchange, command.cutoffEnabled());
                long durationMs = (System.nanoTime() - startedNs) / 1_000_000L;
                zombiesTotal += result.zombieCount();
                observer.onPortfolioPhaseEvaluated("phase2.zombie", result.status().name(), exchange, mode.name(), durationMs);

                switch (result.status()) {
                    case CLEAN -> log.info(
                            "bootSequence.phase2.zombie: portfolio={} exchange={} status={} openOrders={} zombies={}",
                            result.portfolioId(), result.exchangeId(), result.status(),
                            result.openOrders(), result.zombieCount());
                    case DETECTED -> {
                        boolean persistToDlq = mode == BootFailureMode.FAIL_FAST;
                        int persistedSamples = persistToDlq ? persistZombieSamplesToDlq(result) : 0;
                        log.warn(
                                "bootSequence.phase2.zombie: portfolio={} exchange={} mode={} status={} code={} openOrders={} zombies={} invalidFormat={} unknownRunner={} noLocalMatch={} beforeCutoff={} unknownSymbol={} persistedSamples={} dlqPersisted={}",
                                result.portfolioId(), result.exchangeId(), mode, result.status(), result.code(),
                                result.openOrders(), result.zombieCount(), result.invalidFormatCount(),
                                result.unknownRunnerCount(), result.noLocalMatchCount(), result.beforeCutoffCount(),
                                result.unknownSymbolCount(), persistedSamples, persistToDlq);
                        result.samples().forEach(sample -> log.warn(
                                "bootSequence.phase2.zombie: sample portfolio={} exchange={} reason={} code={} clientOrderId={} exchangeOrderId={} symbol={}",
                                result.portfolioId(), result.exchangeId(), sample.reason(), sample.code(),
                                sample.clientOrderId(), sample.exchangeOrderId(), sample.symbol()));
                    }
                    case SKIPPED -> log.warn(
                            "bootSequence.phase2.zombie: portfolio={} exchange={} status={} code={} message={}",
                            result.portfolioId(), result.exchangeId(), result.status(), result.code(), result.message());
                    case FAILED -> log.error(
                            "bootSequence.phase2.zombie: portfolio={} exchange={} status={} code={} message={}",
                            result.portfolioId(), result.exchangeId(), result.status(), result.code(), result.message());
                }

                boolean criticalFailure = result.status() == PortfolioZombieDetectionStatus.FAILED
                        || result.status() == PortfolioZombieDetectionStatus.DETECTED;
                if (criticalFailure && mode == BootFailureMode.FAIL_FAST) {
                    throw triggerFailFast(runId, "phase2.zombie", result.code(),
                            "Phase2 zombie detection failed portfolio=" + result.portfolioId()
                                    + " exchange=" + result.exchangeId()
                                    + " message=" + result.message(),
                            observer);
                }
            }
        }

        log.info("bootSequence.phase2.zombie: completed runId={} zombies={}", runId, zombiesTotal);
    }

    private void runPhase2ReservationTtl(String runId,
                                         List<Portfolio> portfolios,
                                         List<StrategyRunner> eligibleRunners,
                                         BootExecutionCommand command,
                                         BootExecutionObserverPort observer) {
        long ttlMs = command.ttlMs();
        BootFailureMode mode = command.failureMode();
        log.info("bootSequence.phase2.ttl: start portfolios={} mode={} ttlMs={}", portfolios.size(), mode, ttlMs);

        int expiredTotal = 0;
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
                PortfolioReservationTtlResult result =
                        portfolioReservationTtlUseCase.execute(portfolio.getId(), exchange, ttlMs, scopedRunners);
                long durationMs = (System.nanoTime() - startedNs) / 1_000_000L;
                expiredTotal += result.expiredCount();
                observer.onPortfolioPhaseEvaluated("phase2.reservation_ttl", result.status().name(), exchange, mode.name(), durationMs);

                switch (result.status()) {
                    case CLEAN -> log.info(
                            "bootSequence.phase2.ttl: portfolio={} exchange={} status={} ttlMs={} scannedPending={} eligibleNoExchangeOrderId={} expired={} fresh={} errors={}",
                            result.portfolioId(), result.exchangeId(), result.status(), result.ttlMs(),
                            result.scannedPendingCount(), result.eligibleNoExchangeOrderIdCount(),
                            result.expiredCount(), result.freshCount(), result.errorCount());
                    case EXPIRED -> {
                        log.warn(
                                "bootSequence.phase2.ttl: portfolio={} exchange={} status={} code={} ttlMs={} scannedPending={} eligibleNoExchangeOrderId={} expired={} fresh={} errors={}",
                                result.portfolioId(), result.exchangeId(), result.status(), result.code(), result.ttlMs(),
                                result.scannedPendingCount(), result.eligibleNoExchangeOrderIdCount(),
                                result.expiredCount(), result.freshCount(), result.errorCount());
                        result.samples().forEach(transactionId -> log.warn(
                                "bootSequence.phase2.ttl: expiredSample portfolio={} exchange={} transactionId={}",
                                result.portfolioId(), result.exchangeId(), transactionId));
                    }
                    case SKIPPED -> log.warn(
                            "bootSequence.phase2.ttl: portfolio={} exchange={} status={} code={} message={} ttlMs={}",
                            result.portfolioId(), result.exchangeId(), result.status(), result.code(),
                            result.message(), result.ttlMs());
                    case FAILED -> log.error(
                            "bootSequence.phase2.ttl: portfolio={} exchange={} status={} code={} message={} ttlMs={} scannedPending={} eligibleNoExchangeOrderId={} expired={} fresh={} errors={}",
                            result.portfolioId(), result.exchangeId(), result.status(), result.code(), result.message(), result.ttlMs(),
                            result.scannedPendingCount(), result.eligibleNoExchangeOrderIdCount(),
                            result.expiredCount(), result.freshCount(), result.errorCount());
                }

                boolean criticalFailure = result.status() == PortfolioReservationTtlStatus.FAILED;
                if (criticalFailure && mode == BootFailureMode.FAIL_FAST) {
                    throw triggerFailFast(runId, "phase2.reservation_ttl", result.code(),
                            "Phase2 reservation TTL failed portfolio=" + result.portfolioId()
                                    + " exchange=" + result.exchangeId()
                                    + " message=" + result.message(),
                            observer);
                }
            }
        }

        log.info("bootSequence.phase2.ttl: completed runId={} expired={}", runId, expiredTotal);
    }

    private void executePhase(String phase, boolean enabled, Runnable action, BootExecutionObserverPort observer) {
        if (!enabled) {
            observer.onPhaseSkipped(phase);
            return;
        }

        long startedNs = System.nanoTime();
        observer.onPhaseStarted(phase);
        try {
            action.run();
            long durationMs = (System.nanoTime() - startedNs) / 1_000_000L;
            observer.onPhaseSucceeded(phase, durationMs);
        } catch (RuntimeException e) {
            long durationMs = (System.nanoTime() - startedNs) / 1_000_000L;
            observer.onPhaseFailed(phase, durationMs, e.getMessage());
            throw e;
        }
    }

    private IllegalStateException triggerFailFast(String runId,
                                                  String phase,
                                                  String code,
                                                  String message,
                                                  BootExecutionObserverPort observer) {
        observer.onFailFastRequested(runId, phase, code, message);
        return new IllegalStateException(message + " code=" + code);
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

    private Set<String> resolvePortfolioExchanges(Portfolio portfolio, List<StrategyRunner> eligibleRunners) {
        return eligibleRunners.stream()
                .filter(runner -> runner.getPortfolioId().equals(portfolio.getId()))
                .map(StrategyRunner::getExchangeId)
                .filter(exchange -> exchange != null && !exchange.isBlank())
                .map(String::toUpperCase)
                .collect(Collectors.toCollection(java.util.TreeSet::new));
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

        log.info("bootSequence: runner={} inFlight={} zombies={} limbo={}",
                summary.runnerId(), summary.inFlightCount(), summary.zombiesCount(), summary.limboCount());

        if (log.isDebugEnabled()) {
            summary.notes().forEach(note ->
                    log.debug("bootSequence: runner={} plan={}", summary.runnerId(), note));
        }

        return summary;
    }
}
