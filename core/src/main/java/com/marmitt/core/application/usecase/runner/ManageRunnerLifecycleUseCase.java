package com.marmitt.core.application.usecase.runner;

import com.marmitt.core.domain.runner.StrategyRunner;
import com.marmitt.core.dto.runner.request.UpdateRunnerLimitsRequest;
import com.marmitt.core.dto.runner.response.RunnerTransitionResult;
import com.marmitt.core.enums.RunnerStatus;
import com.marmitt.core.enums.TransactionStatus;
import com.marmitt.core.exceptions.RunnerNotFoundException;
import com.marmitt.core.exceptions.RunnerOperationException;
import com.marmitt.core.ports.inbound.runner.ManageRunnerLifecyclePort;
import com.marmitt.core.ports.outbound.metrics.OperationalMetricsPort;
import com.marmitt.core.ports.outbound.repository.ExchangeAdapterRepositoryPort;
import com.marmitt.core.ports.outbound.repository.StrategyRunnerRepositoryPort;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Ciclo de vida do runner por comando do operador (T49, IG §13).
 *
 * <ul>
 *   <li><b>start / resume</b> reconciliam o runner pelo mesmo caminho do boot
 *       ({@link RunnerBootRecoveryUseCase}): ordens em voo consultadas na exchange e DLQ verificada.
 *       O start ativa no proprio passo final da reconciliacao; o resume so retoma se ela terminar
 *       limpa. Antes, a exchange do runner precisa estar disponivel para envio.</li>
 *   <li><b>terminate</b> poe o runner em TERMINATING: sem novas compras, a estrategia ainda vende.
 *       Ele e arquivado quando nao houver posicao nem ordem em voo — na hora, se ja estiver zerado,
 *       ou pelo {@link #archiveCompleted()} periodico.</li>
 * </ul>
 */
@Slf4j
public class ManageRunnerLifecycleUseCase implements ManageRunnerLifecyclePort {

    private final StrategyRunnerRepositoryPort strategyRunnerRepository;
    private final ExchangeAdapterRepositoryPort exchangeAdapterRepository;
    private final RunnerBootRecoveryUseCase runnerRecovery;
    private final OperationalMetricsPort metrics;

    public ManageRunnerLifecycleUseCase(StrategyRunnerRepositoryPort strategyRunnerRepository,
                                        ExchangeAdapterRepositoryPort exchangeAdapterRepository,
                                        RunnerBootRecoveryUseCase runnerRecovery,
                                        OperationalMetricsPort metrics) {
        this.strategyRunnerRepository = strategyRunnerRepository;
        this.exchangeAdapterRepository = exchangeAdapterRepository;
        this.runnerRecovery = runnerRecovery;
        this.metrics = metrics;
    }

    @Override
    public RunnerTransitionResult start(UUID runnerId) {
        StrategyRunner runner = load(runnerId);
        RunnerStatus previous = runner.getStatus();
        requireExchangeAvailable(runner);
        runner.startInitializing();
        strategyRunnerRepository.save(runner);
        if (previous != RunnerStatus.INITIALIZING) {
            metrics.recordTransition(previous, RunnerStatus.INITIALIZING);
        }

        reconcile(runner, "start");
        StrategyRunner started = load(runnerId);
        log.info("runnerLifecycle: started runnerId={} {} -> {}", runnerId, previous, started.getStatus());
        return RunnerTransitionResult.of(started, previous);
    }

    @Override
    public RunnerTransitionResult resume(UUID runnerId) {
        StrategyRunner runner = load(runnerId);
        if (runner.getStatus() != RunnerStatus.HALTED) {
            throw new RunnerOperationException("Only HALTED runners can be resumed; runner " + runnerId
                    + " is " + runner.getStatus());
        }
        requireExchangeAvailable(runner);

        reconcile(runner, "resume");
        StrategyRunner reconciled = load(runnerId);
        reconciled.resume();
        strategyRunnerRepository.save(reconciled);
        metrics.recordTransition(RunnerStatus.HALTED, RunnerStatus.ACTIVE);
        log.info("runnerLifecycle: resumed runnerId={} HALTED -> ACTIVE", runnerId);
        return RunnerTransitionResult.of(reconciled, RunnerStatus.HALTED);
    }

    @Override
    public RunnerTransitionResult terminate(UUID runnerId) {
        StrategyRunner runner = load(runnerId);
        RunnerStatus previous = runner.getStatus();
        runner.startTerminating();
        strategyRunnerRepository.save(runner);
        metrics.recordTransition(previous, RunnerStatus.TERMINATING);
        log.info("runnerLifecycle: terminating runnerId={} {} -> TERMINATING", runnerId, previous);
        archiveIfFlat(runner);
        return RunnerTransitionResult.of(runner, previous);
    }

    @Override
    public void archiveCompleted() {
        for (StrategyRunner runner : strategyRunnerRepository.findAllByStatus(RunnerStatus.TERMINATING)) {
            try {
                archiveIfFlat(runner);
            } catch (RuntimeException e) {
                log.error("runnerLifecycle: archive check failed runnerId={} - {}", runner.getId(), e.getMessage(), e);
            }
        }
    }

    @Override
    public RunnerTransitionResult updateLimits(UUID runnerId, UpdateRunnerLimitsRequest request) {
        StrategyRunner runner = load(runnerId);
        runner.updateLimits(request.maxAllocationPercent(), request.maxOpenPositions(), request.maxPendingOrders(),
                request.maxLossPercentPerLot(), request.maxHoldingDuration());
        strategyRunnerRepository.save(runner);
        log.info("runnerLifecycle: limits updated runnerId={} maxAllocationPercent={} maxOpenPositions={} maxPendingOrders={} "
                        + "maxLossPercentPerLot={} maxHoldingDuration={}",
                runnerId, runner.getMaxAllocationPercent(), runner.getMaxOpenPositions(), runner.getMaxPendingOrders(),
                runner.getPositionProtection().maxLossPercentPerLot(),
                runner.getPositionProtection().maxHoldingDuration());
        return RunnerTransitionResult.of(runner, runner.getStatus());
    }

    /**
     * Reconciliacao do runner (mesmo caminho do boot). Sem conclusao limpa a operacao e recusada; o
     * runner fica como a reconciliacao o deixou e o motivo vai na resposta.
     */
    private void reconcile(StrategyRunner runner, String operation) {
        RunnerBootRecoveryUseCase.RecoverySummary summary = runnerRecovery.recoverRunner(runner);
        if (!summary.completed()) {
            throw new RunnerOperationException("Runner " + runner.getId() + " " + operation
                    + " refused: reconciliation not completed " + summary.notes());
        }
    }

    /**
     * Health check de ativacao (IG §13.3): adapter registrado e envio de ordens liberado.
     */
    private void requireExchangeAvailable(StrategyRunner runner) {
        String exchangeId = runner.getExchangeId();
        if (exchangeAdapterRepository.findAdapter(exchangeId).isEmpty()
                || exchangeAdapterRepository.isDispatchBlocked(exchangeId)) {
            throw new RunnerOperationException("Exchange " + exchangeId + " is not available for runner "
                    + runner.getId());
        }
    }

    private void archiveIfFlat(StrategyRunner runner) {
        if (runner.getStatus() != RunnerStatus.TERMINATING) {
            return;
        }
        boolean hasPosition = strategyRunnerRepository
                .findActivePositionByRunnerIdAndSymbol(runner.getId(), runner.getSymbol()).isPresent();
        boolean hasInFlight = !strategyRunnerRepository
                .findByRunnerIdAndStatuses(runner.getId(), TransactionStatus.IN_FLIGHT).isEmpty();
        if (hasPosition || hasInFlight) {
            return;
        }
        Instant terminatingSince = runner.getStatusChangedAt();
        runner.archive();
        strategyRunnerRepository.save(runner);
        metrics.recordTransition(RunnerStatus.TERMINATING, RunnerStatus.ARCHIVED);
        metrics.recordTerminatingDuration(Duration.between(terminatingSince, runner.getStatusChangedAt()));
        log.info("runnerLifecycle: archived runnerId={} (no position, no order in flight)", runner.getId());
    }

    private StrategyRunner load(UUID runnerId) {
        return strategyRunnerRepository.findById(runnerId).orElseThrow(() -> new RunnerNotFoundException(runnerId));
    }
}
