package com.marmitt.core.application.usecase.runner;

import com.marmitt.core.domain.runner.RunnerStatusCause;
import com.marmitt.core.domain.runner.Position;
import com.marmitt.core.domain.runner.StrategyRunner;
import com.marmitt.core.domain.runner.Transaction;
import com.marmitt.core.dto.runner.request.UpdateRunnerLimitsRequest;
import com.marmitt.core.dto.runner.response.RunnerTransitionResult;
import com.marmitt.core.enums.AccountingPolicyType;
import com.marmitt.core.enums.ExecutionPolicy;
import com.marmitt.core.enums.RunnerStatus;
import com.marmitt.core.enums.TransactionStatus;
import com.marmitt.core.exceptions.RunnerNotFoundException;
import com.marmitt.core.exceptions.RunnerOperationException;
import com.marmitt.core.ports.outbound.exchange.ExchangeAdapterDescriptor;
import com.marmitt.core.ports.outbound.metrics.OperationalMetricsPort;
import com.marmitt.core.ports.outbound.repository.ExchangeAdapterRepositoryPort;
import com.marmitt.core.ports.outbound.repository.StrategyRunnerRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T49: transicoes do runner por comando do operador.
 */
class ManageRunnerLifecycleUseCaseTest {

    private final StrategyRunnerRepositoryPort repository = mock(StrategyRunnerRepositoryPort.class);
    private final ExchangeAdapterRepositoryPort adapters = mock(ExchangeAdapterRepositoryPort.class);
    private final RunnerBootRecoveryUseCase recovery = mock(RunnerBootRecoveryUseCase.class);
    private final OperationalMetricsPort metrics = mock(OperationalMetricsPort.class);
    private final java.util.List<UUID> activations = new java.util.ArrayList<>();
    private final ManageRunnerLifecycleUseCase useCase =
            new ManageRunnerLifecycleUseCase(repository, adapters, recovery, metrics, activations::add);

    private StrategyRunner runner;

    @BeforeEach
    void setUp() {
        runner = newRunner();
        when(repository.findById(runner.getId())).thenReturn(Optional.of(runner));
        ExchangeAdapterDescriptor descriptor = mock(ExchangeAdapterDescriptor.class);
        when(adapters.findAdapter("MOCK")).thenReturn(Optional.of(descriptor));
        when(repository.findByRunnerIdAndStatuses(any(), any())).thenReturn(List.of());
        when(repository.findActivePositionByRunnerIdAndSymbol(any(), any())).thenReturn(Optional.empty());
    }

    @Test
    void startReconcilesAndActivatesACreatedRunner() {
        when(recovery.recoverRunner(runner, RunnerStatusCause.manual("test"))).thenAnswer(invocation -> {
            runner.activate(RunnerStatusCause.manual("test"));   // passo 6 da reconciliacao: INITIALIZING -> ACTIVE
            return summary(true);
        });

        RunnerTransitionResult result = useCase.start(runner.getId(), "test");

        assertEquals(RunnerStatus.CREATED, result.previousStatus());
        assertEquals(RunnerStatus.ACTIVE, result.newStatus());
        assertEquals(java.util.List.of(runner.getId()), activations, "T56: avisa o coordenador de market data");
    }

    @Test
    void startRefusedWhenReconciliationDoesNotComplete() {
        when(recovery.recoverRunner(runner, RunnerStatusCause.manual("test"))).thenReturn(summary(false));

        assertThrows(RunnerOperationException.class, () -> useCase.start(runner.getId(), "test"));
        assertEquals(RunnerStatus.INITIALIZING, runner.getStatus(), "Fica para um novo start retomar");
    }

    @Test
    void startRefusedWhenExchangeIsBlocked() {
        when(adapters.isDispatchBlocked("MOCK")).thenReturn(true);

        assertThrows(RunnerOperationException.class, () -> useCase.start(runner.getId(), "test"));
        verify(recovery, never()).recoverRunner(any(), any());
    }

    @Test
    void resumeReactivatesAHaltedRunnerAfterCleanReconciliation() {
        activate(runner);
        runner.halt(RunnerStatusCause.manual("test"));
        when(recovery.recoverRunner(runner, RunnerStatusCause.manual("test"))).thenReturn(summary(true));

        RunnerTransitionResult result = useCase.resume(runner.getId(), "test");

        assertEquals(RunnerStatus.HALTED, result.previousStatus());
        assertEquals(RunnerStatus.ACTIVE, result.newStatus());
    }

    @Test
    void resumeKeepsTheRunnerHaltedWhenReconciliationFails() {
        activate(runner);
        runner.halt(RunnerStatusCause.manual("test"));
        when(recovery.recoverRunner(runner, RunnerStatusCause.manual("test"))).thenReturn(summary(false));

        assertThrows(RunnerOperationException.class, () -> useCase.resume(runner.getId(), "test"));
        assertEquals(RunnerStatus.HALTED, runner.getStatus());
    }

    @Test
    void resumeOnlyAcceptsHaltedRunners() {
        RunnerOperationException refused = assertThrows(RunnerOperationException.class,
                () -> useCase.resume(runner.getId(), "test"));
        assertEquals(RunnerOperationException.INVALID_STATE, refused.code());
        verify(recovery, never()).recoverRunner(any(), any());
    }

    @Test
    void repeatingACommandAlreadyAppliedReturnsTheCurrentStateWithoutEffect() {
        activate(runner);

        RunnerTransitionResult start = useCase.start(runner.getId(), "test");
        RunnerTransitionResult resume = useCase.resume(runner.getId(), "test");

        assertEquals(RunnerStatus.ACTIVE, start.previousStatus());
        assertEquals(RunnerStatus.ACTIVE, start.newStatus());
        assertEquals(RunnerStatus.ACTIVE, resume.newStatus());
        verify(recovery, never()).recoverRunner(any(), any());
        verify(repository, never()).save(any());

        runner.startTerminating(RunnerStatusCause.manual("test"));
        RunnerTransitionResult terminate = useCase.terminate(runner.getId(), "test");
        assertEquals(RunnerStatus.TERMINATING, terminate.previousStatus());
        assertEquals(RunnerStatus.TERMINATING, terminate.newStatus());
        verify(repository, never()).save(any());
    }

    @Test
    void refusalsCarryAStableCode() {
        when(recovery.recoverRunner(runner, RunnerStatusCause.manual("test"))).thenReturn(summary(false));
        assertEquals("RUNNER_RECONCILIATION_INCOMPLETE",
                assertThrows(RunnerOperationException.class, () -> useCase.start(runner.getId(), "test")).code());

        when(adapters.isDispatchBlocked("MOCK")).thenReturn(true);
        assertEquals("EXCHANGE_UNAVAILABLE",
                assertThrows(RunnerOperationException.class, () -> useCase.start(runner.getId(), "test")).code());
    }

    @Test
    void terminateArchivesAtOnceWhenTheRunnerIsFlat() {
        activate(runner);

        RunnerTransitionResult result = useCase.terminate(runner.getId(), "test");

        assertEquals(RunnerStatus.ACTIVE, result.previousStatus());
        assertEquals(RunnerStatus.ARCHIVED, runner.getStatus());
        // T51: transicoes e tempo em TERMINATING vao para as metricas de lifecycle.
        verify(metrics).recordTransition(RunnerStatus.ACTIVE, RunnerStatus.TERMINATING);
        verify(metrics).recordTransition(RunnerStatus.TERMINATING, RunnerStatus.ARCHIVED);
        verify(metrics).recordTerminatingDuration(any(Duration.class));
    }

    @Test
    void terminateWaitsWhileThereIsAPositionOrAnOrderInFlight() {
        activate(runner);
        Position lot = mock(Position.class);
        when(repository.findActivePositionByRunnerIdAndSymbol(runner.getId(), "BTCUSDT")).thenReturn(Optional.of(lot));

        useCase.terminate(runner.getId(), "test");
        assertEquals(RunnerStatus.TERMINATING, runner.getStatus());

        // A estrategia vendeu o lote, mas ainda ha ordem em voo.
        when(repository.findActivePositionByRunnerIdAndSymbol(runner.getId(), "BTCUSDT")).thenReturn(Optional.empty());
        List<Transaction> inFlight = List.of(mock(Transaction.class));
        when(repository.findByRunnerIdAndStatuses(runner.getId(), TransactionStatus.IN_FLIGHT)).thenReturn(inFlight);
        when(repository.findAllByStatus(RunnerStatus.TERMINATING)).thenReturn(List.of(runner));
        useCase.archiveCompleted();
        assertEquals(RunnerStatus.TERMINATING, runner.getStatus());

        when(repository.findByRunnerIdAndStatuses(runner.getId(), TransactionStatus.IN_FLIGHT)).thenReturn(List.of());
        useCase.archiveCompleted();
        assertEquals(RunnerStatus.ARCHIVED, runner.getStatus());
    }

    @Test
    void updateLimitsAppliesTheInformedFields() {
        activate(runner);

        useCase.updateLimits(runner.getId(), new UpdateRunnerLimitsRequest(new BigDecimal("0.25"), 2, 2,
                new BigDecimal("0.08"), Duration.ofHours(24), null, null, null));

        assertEquals(0, new BigDecimal("0.25").compareTo(runner.getMaxAllocationPercent()));
        assertEquals(2, runner.getMaxOpenPositions());
        assertEquals(2, runner.getMaxPendingOrders());
        assertEquals(0, new BigDecimal("0.08").compareTo(runner.getPositionProtection().maxLossPercentPerLot()));
        assertEquals(Duration.ofHours(24), runner.getPositionProtection().maxHoldingDuration());
    }

    @Test
    void unknownRunnerIsNotFound() {
        assertThrows(RunnerNotFoundException.class, () -> useCase.start(UUID.randomUUID(), "test"));
    }

    private RunnerBootRecoveryUseCase.RecoverySummary summary(boolean completed) {
        return new RunnerBootRecoveryUseCase.RecoverySummary(runner.getId(), 0, 0, List.of(), completed);
    }

    private static void activate(StrategyRunner runner) {
        runner.startInitializing(RunnerStatusCause.manual("test"));
        runner.activate(RunnerStatusCause.manual("test"));
    }

    private static StrategyRunner newRunner() {
        return new StrategyRunner(UUID.randomUUID(), UUID.randomUUID(), "t49", UUID.randomUUID(), "s",
                "BTCUSDT", "MOCK", Set.of(), ExecutionPolicy.SINGLE, AccountingPolicyType.FIFO,
                new BigDecimal("0.20"), 1, 1, null);
    }
}
