package com.marmitt.core.domain.runner;

import com.marmitt.core.enums.AccountingPolicyType;
import com.marmitt.core.enums.ExecutionPolicy;
import com.marmitt.core.enums.RunnerStatus;
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

