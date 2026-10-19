package com.marmitt.core.domain.runner;

import com.marmitt.core.enums.AccountingPolicyType;
import com.marmitt.core.enums.ExecutionPolicy;
import com.marmitt.core.enums.RunnerStatus;
import com.marmitt.core.exceptions.RunnerOperationException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StrategyRunnerInvariantsTest {

    @Test
    void lifecycleTransitionsFollowExpectedOrder() {
        StrategyRunner runner = newRunner();

        runner.startInitializing();
        assertEquals(RunnerStatus.INITIALIZING, runner.getStatus());

        runner.activate();
        assertEquals(RunnerStatus.ACTIVE, runner.getStatus());

        runner.halt();
        assertEquals(RunnerStatus.HALTED, runner.getStatus());

        runner.resume();
        assertEquals(RunnerStatus.ACTIVE, runner.getStatus());

        runner.startTerminating();
        assertEquals(RunnerStatus.TERMINATING, runner.getStatus());

        runner.archive();
        assertEquals(RunnerStatus.ARCHIVED, runner.getStatus());
    }

    @Test
    void beginReconciliationRejectsTerminatingAndArchived() {
        StrategyRunner terminatingRunner = newRunner();
        terminatingRunner.startInitializing();
        terminatingRunner.activate();
        terminatingRunner.startTerminating();

        assertThrows(IllegalStateException.class, terminatingRunner::beginReconciliation);

        StrategyRunner archivedRunner = newRunner();
        archivedRunner.startInitializing();
        archivedRunner.activate();
        archivedRunner.startTerminating();
        archivedRunner.archive();

        assertThrows(IllegalStateException.class, archivedRunner::beginReconciliation);
    }

    @Test
    void canAcceptSignalsOnlyWhenActiveAndNotReconciling() {
        StrategyRunner runner = newRunner();
        assertFalse(runner.canAcceptSignals());

        runner.startInitializing();
        assertFalse(runner.canAcceptSignals());

        runner.activate();
        assertTrue(runner.canAcceptSignals());

        runner.beginReconciliation();
        assertFalse(runner.canAcceptSignals());

        runner.completeReconciliation();
        assertTrue(runner.canAcceptSignals());
    }

    @Test
    void shortCodeIsNormalizedToLowerCase() {
        StrategyRunner runner = new StrategyRunner(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "A1B",
                UUID.randomUUID(),
                "SimpleMovingAverage",
                "BTCUSDT",
                "MOCK",
                Set.of("BINANCE", "MOCK"),
                ExecutionPolicy.SINGLE,
                AccountingPolicyType.FIFO,
                new BigDecimal("0.25"),
                1,
                1,
                null
        );

        assertEquals("a1b", runner.getShortCode());
    }

    @Test
    void terminatingRunnerStillSellsButCannotOpenPositions() {
        // T49: em encerramento o pipeline segue conduzindo o runner (vendas), mas nao abre posicao.
        StrategyRunner runner = activeRunner();
        assertTrue(runner.canOpenPositions());

        runner.startTerminating();

        assertTrue(runner.canAcceptSignals());
        assertFalse(runner.canOpenPositions());
    }

    @Test
    void killSwitchAlsoHaltsATerminatingRunner() {
        StrategyRunner runner = activeRunner();
        runner.startTerminating();

        runner.halt();

        assertEquals(RunnerStatus.HALTED, runner.getStatus());
        assertFalse(runner.canAcceptSignals());
    }

    @Test
    void invalidTransitionsRaiseRunnerOperationException() {
        StrategyRunner created = newRunner();

        assertThrows(RunnerOperationException.class, created::resume);
        assertThrows(RunnerOperationException.class, created::halt);
        assertThrows(RunnerOperationException.class, created::startTerminating);
    }

    @Test
    void startInitializingIsRetriableWhileInitializing() {
        StrategyRunner runner = newRunner();
        runner.startInitializing();

        runner.startInitializing();

        assertEquals(RunnerStatus.INITIALIZING, runner.getStatus());
    }

    @Test
    void updateLimitsChangesOnlyInformedFieldsAndValidates() {
        StrategyRunner runner = activeRunner();
        int pendingBefore = runner.getMaxPendingOrders();

        runner.updateLimits(new BigDecimal("0.30"), 3, null);

        assertEquals(0, new BigDecimal("0.30").compareTo(runner.getMaxAllocationPercent()));
        assertEquals(3, runner.getMaxOpenPositions());
        assertEquals(pendingBefore, runner.getMaxPendingOrders());
        assertThrows(IllegalArgumentException.class, () -> runner.updateLimits(new BigDecimal("1.5"), null, null));
        assertThrows(IllegalArgumentException.class, () -> runner.updateLimits(null, 0, null));
    }

    private static StrategyRunner activeRunner() {
        StrategyRunner runner = newRunner();
        runner.startInitializing();
        runner.activate();
        return runner;
    }

    private static StrategyRunner newRunner() {
        return new StrategyRunner(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "x1",
                UUID.randomUUID(),
                "SimpleMovingAverage",
                "BTCUSDT",
                "MOCK",
                Set.of("BINANCE", "MOCK"),
                ExecutionPolicy.SINGLE,
                AccountingPolicyType.FIFO,
                new BigDecimal("0.25"),
                1,
                1,
                null
        );
    }
}
