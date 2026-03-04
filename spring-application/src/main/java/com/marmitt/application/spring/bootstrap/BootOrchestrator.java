package com.marmitt.application.spring.bootstrap;

import com.marmitt.core.application.usecase.runner.RunnerBootRecoveryUseCase;
import com.marmitt.core.application.usecase.portfolio.PortfolioBootSanityUseCase;
import com.marmitt.core.application.usecase.portfolio.PortfolioReservationTtlUseCase;
import com.marmitt.core.application.usecase.portfolio.PortfolioZombieDetectionUseCase;
import com.marmitt.core.domain.portfolio.DeadLetterEntry;
import com.marmitt.core.domain.portfolio.Portfolio;
import com.marmitt.core.domain.runner.StrategyRunner;
import com.marmitt.core.dto.portfolio.PortfolioBootSanityResult;
import com.marmitt.core.dto.portfolio.PortfolioZombieCandidate;
import com.marmitt.core.dto.portfolio.PortfolioReservationTtlResult;
import com.marmitt.core.dto.portfolio.PortfolioZombieDetectionResult;
import com.marmitt.core.dto.exchange.boot.ExchangeBootReadiness;
import com.marmitt.core.enums.PortfolioReservationTtlStatus;
import com.marmitt.core.enums.RunnerStatus;
import com.marmitt.core.enums.PortfolioSanityStatus;
import com.marmitt.core.enums.PortfolioZombieDetectionStatus;
import com.marmitt.core.ports.outbound.repository.DeadLetterEntryRepositoryPort;
import com.marmitt.core.ports.outbound.repository.ExchangeAdapterRepositoryPort;
import com.marmitt.core.ports.outbound.repository.PortfolioRepositoryPort;
import com.marmitt.core.ports.outbound.repository.StrategyRunnerRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Orquestrador de boot em nivel de aplicacao.
 *
 * <p>Responsabilidade nesta fase:
 * <ul>
 *   <li>Demonstrar a ordem de fases do boot recovery.</li>
 *   <li>Disparar o RunnerBootRecoveryUseCase para cada runner elegivel.</li>
 *   <li>Executar saneamento/reconciliacao por runner e registrar resumo em log.</li>
 * </ul>
 *
 * <p>Ativacao:
 * <ul>
 *   <li>{@code runner.boot.orchestrator-enabled=true}</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "runner.boot.orchestrator-enabled",
        havingValue = "true",
        matchIfMissing = false
)
public class BootOrchestrator {

    private final PortfolioRepositoryPort portfolioRepository;
    private final StrategyRunnerRepositoryPort strategyRunnerRepository;
    private final ExchangeAdapterRepositoryPort exchangeAdapterRepository;
    private final PortfolioBootSanityUseCase portfolioBootSanityUseCase;
    private final PortfolioReservationTtlUseCase portfolioReservationTtlUseCase;
    private final PortfolioZombieDetectionUseCase portfolioZombieDetectionUseCase;
    private final DeadLetterEntryRepositoryPort deadLetterEntryRepository;
    private final RunnerBootPhase1Properties phase1Properties;
    private final RunnerBootPhase2Properties phase2Properties;
    private final RunnerBootPhase3Properties phase3Properties;
    private final PortfolioSanityCheckProperties portfolioSanityCheckProperties;
    private final PortfolioReservationTtlProperties portfolioReservationTtlProperties;
    private final PortfolioZombieDetectionProperties portfolioZombieDetectionProperties;
    private final PortfolioCutoffProperties portfolioCutoffProperties;
    private final RunnerBootRecoveryUseCase runnerBootRecoveryUseCase;
    private final BootStatusTracker bootStatusTracker;
    private final BootMetricsRecorder bootMetricsRecorder;
    private final ApplicationEventPublisher applicationEventPublisher;

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        bootStatusTracker.startRun(phase2Properties.getMode().name());
        log.info("bootOrchestrator: start runId={} mode={} phase1Enabled={} phase2Enabled={} phase3Enabled={}",
                bootStatusTracker.currentRunId(),
                phase2Properties.getMode(),
                phase1Properties.isEnabled(),
                phase2Properties.isEnabled(),
                phase3Properties.isEnabled());

        try {
            List<Portfolio> portfolios = portfolioRepository.findAll();
            List<StrategyRunner> eligibleRunners = portfolios.stream()
                    .flatMap(this::loadRunnersByPortfolio)
                    .filter(this::isEligibleForRecovery)
                    .toList();

            executePhase("phase1.infrastructure", phase1Properties.isEnabled(),
                    () -> runPhase1InfrastructureReadiness(eligibleRunners));

            boolean phase2Enabled = phase2Properties.isEnabled();
            executePhase("phase2.sanity",
                    phase2Enabled && portfolioSanityCheckProperties.isEnabled(),
                    () -> runPhase2PortfolioSanity(portfolios, eligibleRunners));
            executePhase("phase2.zombie",
                    phase2Enabled && portfolioZombieDetectionProperties.isEnabled(),
                    () -> runPhase2ZombieDetection(portfolios, eligibleRunners));
            executePhase("phase2.reservation_ttl",
                    phase2Enabled && portfolioReservationTtlProperties.isEnabled(),
                    () -> runPhase2ReservationTtl(portfolios, eligibleRunners));

            List<RunnerBootRecoveryUseCase.RecoverySummary> summaries = new ArrayList<>();
            executePhase("phase3.runner_recovery", phase3Properties.isEnabled(),
                    () -> summaries.addAll(eligibleRunners.stream().map(this::recoverRunner).toList()));

            bootStatusTracker.completeRun();
            bootMetricsRecorder.recordRun(BootRunStatus.SUCCESS);
            log.info("bootOrchestrator: completed runId={} portfolios={} runners={}",
                    bootStatusTracker.currentRunId(), portfolios.size(), summaries.size());
        } catch (RuntimeException e) {
            bootStatusTracker.failRun("boot", e.getMessage());
            bootMetricsRecorder.recordRun(BootRunStatus.FAILED);
            log.error("bootOrchestrator: failed runId={} reason={}",
                    bootStatusTracker.currentRunId(), e.getMessage(), e);
            throw e;
        }
    }

    private void runPhase1InfrastructureReadiness(List<StrategyRunner> runners) {
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
                throw failFast(
                        "phase1.infrastructure",
                        readiness.code(),
                        "Phase1 readiness failed exchange=" + exchange + " message=" + readiness.message()
                );
            }

            log.info("bootOrchestrator.phase1: exchange={} ready code={} message={}",
                    exchange, readiness.code(), readiness.message());
        }

        log.info("bootOrchestrator.phase1: completed exchanges={}", exchanges.size());
    }

    private void runPhase2PortfolioSanity(List<Portfolio> portfolios, List<StrategyRunner> eligibleRunners) {
        Phase2Mode mode = phase2Properties.getMode();
        log.info("bootOrchestrator.phase2.sanity: start portfolios={} mode={} accountQueryPolicy={} threshold={}",
                portfolios.size(), mode, phase2Properties.getAccountQueryPolicy(), portfolioSanityCheckProperties.getThreshold());

        for (Portfolio portfolio : portfolios) {
            Set<String> exchanges = resolvePortfolioExchanges(portfolio, eligibleRunners);

            if (exchanges.isEmpty()) {
                log.debug("bootOrchestrator.phase2.sanity: portfolio={} skipped - no eligible runner exchange",
                        portfolio.getId());
                continue;
            }

            for (String exchange : exchanges) {
                long startedNs = System.nanoTime();
                PortfolioBootSanityResult result =
                        portfolioBootSanityUseCase.execute(
                                portfolio.getId(),
                                exchange,
                                portfolioSanityCheckProperties.getThreshold()
                        );
                long durationMs = (System.nanoTime() - startedNs) / 1_000_000L;
                bootMetricsRecorder.recordPortfolioPhaseEvaluation(
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
                        && phase2Properties.getAccountQueryPolicy() == Phase2AccountQueryPolicy.FAIL;
                boolean criticalFailure = result.status() == PortfolioSanityStatus.FAIL_DEFICIT
                        || result.status() == PortfolioSanityStatus.FAILED;
                if ((skippedWithFailPolicy || criticalFailure) && mode == Phase2Mode.FAIL_FAST) {
                    throw failFast(
                            "phase2.sanity",
                            result.code(),
                            "Phase2 sanity failed portfolio=" + result.portfolioId()
                                    + " exchange=" + result.exchangeId()
                                    + " message=" + result.message()
                    );
                }
            }
        }

        log.info("bootOrchestrator.phase2.sanity: completed");
    }

    private void runPhase2ZombieDetection(List<Portfolio> portfolios, List<StrategyRunner> eligibleRunners) {
        Phase2Mode mode = phase2Properties.getMode();
        log.info("bootOrchestrator.phase2.zombie: start portfolios={} mode={} cutoffEnabled={}",
                portfolios.size(), mode, portfolioCutoffProperties.isEnabled());

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
                                portfolioCutoffProperties.isEnabled()
                        );
                long durationMs = (System.nanoTime() - startedNs) / 1_000_000L;
                bootMetricsRecorder.recordPortfolioPhaseEvaluation(
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
                        int persistedSamples = persistZombieSamplesToDlq(result);
                        log.warn(
                                "bootOrchestrator.phase2.zombie: portfolio={} exchange={} status={} code={} openOrders={} zombies={} invalidFormat={} unknownRunner={} noLocalMatch={} beforeCutoff={} unknownSymbol={} persistedSamples={}",
                                result.portfolioId(),
                                result.exchangeId(),
                                result.status(),
                                result.code(),
                                result.openOrders(),
                                result.zombieCount(),
                                result.invalidFormatCount(),
                                result.unknownRunnerCount(),
                                result.noLocalMatchCount(),
                                result.beforeCutoffCount(),
                                result.unknownSymbolCount(),
                                persistedSamples
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
                if (criticalFailure && mode == Phase2Mode.FAIL_FAST) {
                    throw failFast(
                            "phase2.zombie",
                            result.code(),
                            "Phase2 zombie detection failed portfolio=" + result.portfolioId()
                                    + " exchange=" + result.exchangeId()
                                    + " message=" + result.message()
                    );
                }
            }
        }

        log.info("bootOrchestrator.phase2.zombie: completed");
    }

    private void runPhase2ReservationTtl(List<Portfolio> portfolios, List<StrategyRunner> eligibleRunners) {
        long ttlMs = portfolioReservationTtlProperties.getTtlMs();
        Phase2Mode mode = phase2Properties.getMode();
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
                bootMetricsRecorder.recordPortfolioPhaseEvaluation(
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
                if (criticalFailure && mode == Phase2Mode.FAIL_FAST) {
                    throw failFast(
                            "phase2.reservation_ttl",
                            result.code(),
                            "Phase2 reservation TTL failed portfolio=" + result.portfolioId()
                                    + " exchange=" + result.exchangeId()
                                    + " message=" + result.message()
                    );
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
        return runner.getStatus() != RunnerStatus.ARCHIVED;
    }

    private RunnerBootRecoveryUseCase.RecoverySummary recoverRunner(StrategyRunner runner) {
        RunnerBootRecoveryUseCase.RecoverySummary summary = runnerBootRecoveryUseCase.recoverRunner(runner);

        log.info("bootOrchestrator: runner={} inFlight={} zombies={} limbo={}",
                summary.runnerId(), summary.inFlightCount(), summary.zombiesCount(), summary.limboCount());

        // Verbose somente em debug para nao poluir log de producao.
        if (log.isDebugEnabled()) {
            summary.notes().forEach(note ->
                    log.debug("bootOrchestrator: runner={} plan={}", summary.runnerId(), note));
        }

        return summary;
    }

    private void executePhase(String phase, boolean enabled, Runnable action) {
        if (!enabled) {
            bootStatusTracker.completePhase(phase, BootPhaseStatus.SKIPPED, "disabled_by_configuration");
            bootMetricsRecorder.recordPhase(phase, BootPhaseStatus.SKIPPED, 0L);
            log.info("bootOrchestrator: phase={} status=SKIPPED reason=disabled_by_configuration runId={}",
                    phase, bootStatusTracker.currentRunId());
            return;
        }

        long startedNs = System.nanoTime();
        bootStatusTracker.startPhase(phase);
        try {
            action.run();
            long durationMs = (System.nanoTime() - startedNs) / 1_000_000L;
            bootStatusTracker.completePhase(phase, BootPhaseStatus.SUCCESS, "ok");
            bootMetricsRecorder.recordPhase(phase, BootPhaseStatus.SUCCESS, durationMs);
            log.info("bootOrchestrator: phase={} status=SUCCESS durationMs={} runId={}",
                    phase, durationMs, bootStatusTracker.currentRunId());
        } catch (RuntimeException e) {
            long durationMs = (System.nanoTime() - startedNs) / 1_000_000L;
            bootStatusTracker.completePhase(phase, BootPhaseStatus.FAILED, e.getMessage());
            bootMetricsRecorder.recordPhase(phase, BootPhaseStatus.FAILED, durationMs);
            bootStatusTracker.failRun(phase, e.getMessage());
            throw e;
        }
    }

    private IllegalStateException failFast(String phase, String code, String message) {
        bootMetricsRecorder.recordFailFast(phase, code);
        applicationEventPublisher.publishEvent(new BootFailFastEvent(
                bootStatusTracker.currentRunId(),
                phase,
                code,
                message,
                Instant.now()
        ));
        return new IllegalStateException(message + " code=" + code);
    }

    private int persistZombieSamplesToDlq(PortfolioZombieDetectionResult result) {
        int persisted = 0;
        for (PortfolioZombieCandidate sample : result.samples()) {
            boolean alreadyOpen = deadLetterEntryRepository.existsUnresolvedByIdentity(
                    result.portfolioId(),
                    sample.clientOrderId(),
                    sample.exchangeOrderId(),
                    sample.reason()
            );

            if (alreadyOpen) {
                continue;
            }

            DeadLetterEntry entry = new DeadLetterEntry(
                    result.portfolioId(),
                    sample.clientOrderId(),
                    sample.exchangeOrderId(),
                    "source=boot.phase2.zombie"
                            + ", exchange=" + result.exchangeId()
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
}
