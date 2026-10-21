package com.marmitt.core.application.usecase.boot;

import com.marmitt.core.application.usecase.exchange.ExchangeBalanceReconciliation;
import com.marmitt.core.application.usecase.boot.phase2.PortfolioZombieDetectionUseCase;
import com.marmitt.core.application.usecase.portfolio.AutomaticSafeModeTrigger;
import com.marmitt.core.application.usecase.runner.RunnerBootRecoveryUseCase;
import com.marmitt.core.domain.portfolio.DeadLetterEntry;
import com.marmitt.core.domain.portfolio.Portfolio;
import com.marmitt.core.domain.runner.ClientOrderId;
import com.marmitt.core.domain.runner.RunnerStatusCause;
import com.marmitt.core.domain.runner.StrategyRunner;
import com.marmitt.core.dto.boot.BootExecutionCommand;
import com.marmitt.core.dto.boot.BootRun;
import com.marmitt.core.dto.exchange.boot.ExchangeBootReadiness;
import com.marmitt.core.dto.exchange.ExchangeBalanceReport;
import com.marmitt.core.dto.portfolio.PortfolioZombieCandidate;
import com.marmitt.core.dto.portfolio.PortfolioZombieDetectionResult;
import com.marmitt.core.enums.BootAccountQueryPolicy;
import com.marmitt.core.enums.BootFailureMode;
import com.marmitt.core.enums.BootRunStatus;
import com.marmitt.core.enums.ExchangeBalanceStatus;
import com.marmitt.core.enums.PortfolioZombieDetectionStatus;
import com.marmitt.core.enums.RunnerStatus;
import com.marmitt.core.enums.SafeModeTrigger;
import com.marmitt.core.ports.inbound.boot.RunBootSequencePort;
import com.marmitt.core.ports.outbound.boot.BootExecutionObserverPort;
import com.marmitt.core.ports.outbound.repository.BootRunRepositoryPort;
import com.marmitt.core.ports.outbound.repository.DeadLetterEntryRepositoryPort;
import com.marmitt.core.ports.outbound.repository.ExchangeAdapterRepositoryPort;
import com.marmitt.core.ports.outbound.repository.PortfolioRepositoryPort;
import com.marmitt.core.ports.outbound.repository.StrategyRunnerRepositoryPort;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
public class RunBootSequenceUseCase implements RunBootSequencePort {

    /** Causa das transicoes que a reconciliacao do boot faz no runner (T59). */
    private static final RunnerStatusCause BOOT_RECOVERY = RunnerStatusCause.boot("boot runner recovery");

    private final PortfolioRepositoryPort portfolioRepository;
    private final StrategyRunnerRepositoryPort strategyRunnerRepository;
    private final ExchangeAdapterRepositoryPort exchangeAdapterRepository;
    private final ExchangeBalanceReconciliation exchangeBalanceReconciliation;
    private final PortfolioZombieDetectionUseCase portfolioZombieDetectionUseCase;
    private final DeadLetterEntryRepositoryPort deadLetterEntryRepository;
    private final RunnerBootRecoveryUseCase runnerBootRecoveryUseCase;
    private final AutomaticSafeModeTrigger safeModeTrigger;
    private final BootRunRepositoryPort bootRunRepository;

    public RunBootSequenceUseCase(PortfolioRepositoryPort portfolioRepository,
                                  StrategyRunnerRepositoryPort strategyRunnerRepository,
                                  ExchangeAdapterRepositoryPort exchangeAdapterRepository,
                                  ExchangeBalanceReconciliation exchangeBalanceReconciliation,
                                  PortfolioZombieDetectionUseCase portfolioZombieDetectionUseCase,
                                  DeadLetterEntryRepositoryPort deadLetterEntryRepository,
                                  RunnerBootRecoveryUseCase runnerBootRecoveryUseCase,
                                  AutomaticSafeModeTrigger safeModeTrigger,
                                  BootRunRepositoryPort bootRunRepository) {
        this.portfolioRepository = portfolioRepository;
        this.strategyRunnerRepository = strategyRunnerRepository;
        this.exchangeAdapterRepository = exchangeAdapterRepository;
        this.exchangeBalanceReconciliation = exchangeBalanceReconciliation;
        this.portfolioZombieDetectionUseCase = portfolioZombieDetectionUseCase;
        this.deadLetterEntryRepository = deadLetterEntryRepository;
        this.runnerBootRecoveryUseCase = runnerBootRecoveryUseCase;
        this.safeModeTrigger = safeModeTrigger;
        this.bootRunRepository = bootRunRepository;
    }

    @Override
    public void execute(BootExecutionCommand command, BootExecutionObserverPort observer) {
        String runId = UUID.randomUUID().toString();
        String mode = command.failureMode().name();
        Instant startedAt = Instant.now();
        AtomicReference<String> failedPhase = new AtomicReference<>();
        observer.onRunStarted(runId, mode);
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
                    observer, failedPhase);

            boolean phase2Enabled = command.phase2Enabled();
            executePhase("phase2.sanity", phase2Enabled && command.sanityEnabled(),
                    () -> runPhase2BalanceSanity(runId, command, observer),
                    observer, failedPhase);
            executePhase("phase2.zombie", phase2Enabled && command.zombieEnabled(),
                    () -> runPhase2ZombieDetection(runId, portfolios, eligibleRunners, command, observer),
                    observer, failedPhase);

            List<RunnerBootRecoveryUseCase.RecoverySummary> summaries = new ArrayList<>();
            executePhase("phase3.runner_recovery", command.phase3Enabled(),
                    () -> summaries.addAll(eligibleRunners.stream().map(this::recoverRunner).toList()),
                    observer, failedPhase);

            recordRun(new BootRun(runId, mode, BootRunStatus.SUCCESS, startedAt, Instant.now(), null, null));
            observer.onRunCompleted(runId);
            log.info("bootSequence: completed runId={} portfolios={} runners={}",
                    runId, portfolios.size(), summaries.size());
        } catch (RuntimeException e) {
            recordRun(new BootRun(runId, mode, BootRunStatus.FAILED, startedAt, Instant.now(), failedPhase.get(),
                    e.getMessage()));
            observer.onRunFailed(runId, null, e.getMessage());
            throw e;
        }
    }

    /**
     * Historico do boot (T59). Falha ao gravar nao derruba o boot: um boot que falhou por falta de
     * banco nao tem onde registrar, e o log continua sendo a fonte nesse caso.
     */
    private void recordRun(BootRun run) {
        try {
            bootRunRepository.save(run);
        } catch (RuntimeException e) {
            log.warn("bootSequence: boot run not recorded runId={} status={} - {}", run.runId(), run.status(),
                    e.getMessage());
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

    /**
     * Conferencia de saldo por exchange (T57): soma dos portfolios contra a conta. {@code DEFICIT} poe em
     * HALT todos os portfolios da exchange (nao da para atribuir a falta a um so) antes de qualquer BUY;
     * persistido, vale tambem se o fail-fast abortar o boot a seguir. {@code PENDING} (falta coberta por
     * ordens em voo, que a fase 3 concilia) so registra.
     */
    private void runPhase2BalanceSanity(String runId,
                                        BootExecutionCommand command,
                                        BootExecutionObserverPort observer) {
        BootFailureMode mode = command.failureMode();
        log.info("bootSequence.phase2.sanity: start mode={} accountQueryPolicy={}", mode, command.accountQueryPolicy());

        ExchangeBalanceStatus aggregateResult = null;
        long startedNs = System.nanoTime();
        List<ExchangeBalanceReport> reports = exchangeBalanceReconciliation.reconcileAll();
        long durationMs = (System.nanoTime() - startedNs) / 1_000_000L;
        for (ExchangeBalanceReport report : reports) {
            observer.onPortfolioPhaseEvaluated("phase2.sanity", report.status().name(), report.exchange(),
                    mode.name(), durationMs);
            aggregateResult = aggregateResult == null || report.status().compareTo(aggregateResult) > 0
                    ? report.status() : aggregateResult;

            switch (report.status()) {
                case OK -> log.info("bootSequence.phase2.sanity: portfolios={} {}",
                        report.portfolios().size(), report.details());
                case PENDING, SKIPPED, FAILED -> log.warn("bootSequence.phase2.sanity: portfolios={} {}",
                        report.portfolios().size(), report.details());
                case DEFICIT -> {
                    log.error("bootSequence.phase2.sanity: portfolios={} {}",
                            report.portfolios().size(), report.details());
                    report.portfolios().forEach(portfolioId -> safeModeTrigger.activate(portfolioId,
                            SafeModeTrigger.BALANCE_DIVERGENCE, report.details()));
                }
            }

            boolean skippedWithFailPolicy = report.status() == ExchangeBalanceStatus.SKIPPED
                    && command.accountQueryPolicy() == BootAccountQueryPolicy.FAIL;
            boolean criticalFailure = report.status() == ExchangeBalanceStatus.DEFICIT
                    || report.status() == ExchangeBalanceStatus.FAILED;
            if ((skippedWithFailPolicy || criticalFailure) && mode == BootFailureMode.FAIL_FAST) {
                throw triggerFailFast(runId, "phase2.sanity", report.code(),
                        "Phase2 sanity failed " + report.details(), observer);
            }
        }

        log.info("bootSequence.phase2.sanity: completed runId={} result={} exchanges={}",
                runId, aggregateResult != null ? aggregateResult.name() : "NONE", reports.size());
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

        log.info("bootSequence.phase2.zombie: completed runId={} detected={} mode={}",
                runId, zombiesTotal, mode.name());
    }

    private void executePhase(String phase, boolean enabled, Runnable action, BootExecutionObserverPort observer,
                              AtomicReference<String> failedPhase) {
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
            failedPhase.set(phase);
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
        RunnerBootRecoveryUseCase.RecoverySummary summary = runnerBootRecoveryUseCase.recoverRunner(runner, BOOT_RECOVERY);

        log.info("bootSequence: runner={} inFlight={} limbo={}",
                summary.runnerId(), summary.inFlightCount(), summary.limboCount());

        if (log.isDebugEnabled()) {
            summary.notes().forEach(note ->
                    log.debug("bootSequence: runner={} plan={}", summary.runnerId(), note));
        }

        return summary;
    }
}
