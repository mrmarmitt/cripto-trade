package com.marmitt.core.domain.runner;

import com.marmitt.core.enums.AccountingPolicyType;
import com.marmitt.core.enums.ExecutionPolicy;
import com.marmitt.core.enums.OrderType;
import com.marmitt.core.enums.RunnerStatus;
import com.marmitt.core.enums.RunnerStatusOrigin;
import com.marmitt.core.exceptions.RunnerOperationException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
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

        runner.startInitializing(RunnerStatusCause.manual("test"));
        assertEquals(RunnerStatus.INITIALIZING, runner.getStatus());

        runner.activate(RunnerStatusCause.manual("test"));
        assertEquals(RunnerStatus.ACTIVE, runner.getStatus());

        runner.halt(RunnerStatusCause.manual("test"));
        assertEquals(RunnerStatus.HALTED, runner.getStatus());

        runner.resume(RunnerStatusCause.manual("test"));
        assertEquals(RunnerStatus.ACTIVE, runner.getStatus());

        runner.startTerminating(RunnerStatusCause.manual("test"));
        assertEquals(RunnerStatus.TERMINATING, runner.getStatus());

        runner.archive(RunnerStatusCause.manual("test"));
        assertEquals(RunnerStatus.ARCHIVED, runner.getStatus());
    }

    @Test
    void everyTransitionIsRecordedWithItsCauseUntilTheRepositoryDrainsIt() {
        StrategyRunner runner = newRunner();

        runner.startInitializing(RunnerStatusCause.manual("operator start"));
        runner.startInitializing(RunnerStatusCause.manual("retry start"));   // ja INITIALIZING: sem transicao
        runner.activate(RunnerStatusCause.manual("operator start"));
        runner.halt(RunnerStatusCause.automatic("consecutive order rejections"));

        List<RunnerStatusChange> changes = runner.drainStatusChanges();
        assertEquals(List.of("CREATED>INITIALIZING:MANUAL:operator start", "INITIALIZING>ACTIVE:MANUAL:operator start",
                        "ACTIVE>HALTED:AUTOMATIC:consecutive order rejections"),
                changes.stream().map(c -> c.from() + ">" + c.to() + ":" + c.origin() + ":" + c.reason()).toList());
        assertTrue(changes.stream().allMatch(c -> c.runnerId().equals(runner.getId())));
        assertEquals(runner.getStatusChangedAt(), changes.getLast().changedAt());
        assertTrue(runner.drainStatusChanges().isEmpty(), "gravado uma vez so");

        runner.resume(RunnerStatusCause.boot("boot runner recovery"));
        assertEquals(RunnerStatusOrigin.BOOT, runner.drainStatusChanges().getFirst().origin());
    }

    @Test
    void refusedTransitionRecordsNothingAndCauseIsMandatory() {
        StrategyRunner runner = newRunner();

        assertThrows(RunnerOperationException.class, () -> runner.halt(RunnerStatusCause.manual("test")));
        assertThrows(NullPointerException.class, () -> runner.startInitializing(null));
        assertThrows(IllegalArgumentException.class, () -> RunnerStatusCause.manual(" "));
        assertTrue(runner.drainStatusChanges().isEmpty());
    }

    @Test
    void beginReconciliationRejectsTerminatingAndArchived() {
        StrategyRunner terminatingRunner = newRunner();
        terminatingRunner.startInitializing(RunnerStatusCause.manual("test"));
        terminatingRunner.activate(RunnerStatusCause.manual("test"));
        terminatingRunner.startTerminating(RunnerStatusCause.manual("test"));

        assertThrows(IllegalStateException.class, terminatingRunner::beginReconciliation);

        StrategyRunner archivedRunner = newRunner();
        archivedRunner.startInitializing(RunnerStatusCause.manual("test"));
        archivedRunner.activate(RunnerStatusCause.manual("test"));
        archivedRunner.startTerminating(RunnerStatusCause.manual("test"));
        archivedRunner.archive(RunnerStatusCause.manual("test"));

        assertThrows(IllegalStateException.class, archivedRunner::beginReconciliation);
    }

    @Test
    void canAcceptSignalsOnlyWhenActiveAndNotReconciling() {
        StrategyRunner runner = newRunner();
        assertFalse(runner.canAcceptSignals());

        runner.startInitializing(RunnerStatusCause.manual("test"));
        assertFalse(runner.canAcceptSignals());

        runner.activate(RunnerStatusCause.manual("test"));
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

        runner.startTerminating(RunnerStatusCause.manual("test"));

        assertTrue(runner.canAcceptSignals());
        assertFalse(runner.canOpenPositions());
    }

    @Test
    void killSwitchAlsoHaltsATerminatingRunner() {
        StrategyRunner runner = activeRunner();
        runner.startTerminating(RunnerStatusCause.manual("test"));

        runner.halt(RunnerStatusCause.manual("test"));

        assertEquals(RunnerStatus.HALTED, runner.getStatus());
        assertFalse(runner.canAcceptSignals());
    }

    @Test
    void invalidTransitionsRaiseRunnerOperationException() {
        StrategyRunner created = newRunner();

        assertThrows(RunnerOperationException.class, () -> created.resume(RunnerStatusCause.manual("test")));
        assertThrows(RunnerOperationException.class, () -> created.halt(RunnerStatusCause.manual("test")));
        assertThrows(RunnerOperationException.class, () -> created.startTerminating(RunnerStatusCause.manual("test")));
    }

    @Test
    void startInitializingIsRetriableWhileInitializing() {
        StrategyRunner runner = newRunner();
        runner.startInitializing(RunnerStatusCause.manual("test"));

        runner.startInitializing(RunnerStatusCause.manual("test"));

        assertEquals(RunnerStatus.INITIALIZING, runner.getStatus());
    }

    @Test
    void updateLimitsChangesOnlyInformedFieldsAndValidates() {
        StrategyRunner runner = activeRunner();
        int pendingBefore = runner.getMaxPendingOrders();

        runner.updateLimits(new BigDecimal("0.30"), 3, null, null, null, null, null, null);

        assertEquals(0, new BigDecimal("0.30").compareTo(runner.getMaxAllocationPercent()));
        assertEquals(3, runner.getMaxOpenPositions());
        assertEquals(pendingBefore, runner.getMaxPendingOrders());
        assertThrows(IllegalArgumentException.class, () -> runner.updateLimits(new BigDecimal("1.5"), null, null, null, null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> runner.updateLimits(null, 0, null, null, null, null, null, null));
    }

    @Test
    void newRunnerIsProtectedByTheDefaultLossLimit() {
        PositionProtection protection = newRunner().getPositionProtection();

        assertEquals(0, PositionProtection.DEFAULT_MAX_LOSS_PERCENT_PER_LOT.compareTo(protection.maxLossPercentPerLot()));
        assertEquals(null, protection.maxHoldingDuration(), "Idade maxima do lote desligada por padrao");
    }

    @Test
    void updateLimitsChangesTheLotProtectionAndZeroDisablesTheHoldingLimit() {
        StrategyRunner runner = activeRunner();

        runner.updateLimits(null, null, null, new BigDecimal("0.03"), Duration.ofHours(6), null, null, null);
        assertEquals(0, new BigDecimal("0.03").compareTo(runner.getPositionProtection().maxLossPercentPerLot()));
        assertEquals(Duration.ofHours(6), runner.getPositionProtection().maxHoldingDuration());

        runner.updateLimits(null, null, null, null, Duration.ZERO, null, null, null);
        assertEquals(null, runner.getPositionProtection().maxHoldingDuration());
        assertEquals(0, new BigDecimal("0.03").compareTo(runner.getPositionProtection().maxLossPercentPerLot()));
    }

    @Test
    void liquidationPolicyDefaultsToMarketAndUpdatesOnlyInformedFields() {
        StrategyRunner runner = activeRunner();
        assertEquals(LiquidationPolicy.defaults(), runner.getLiquidationPolicy());

        runner.updateLimits(null, null, null, null, null, OrderType.LIMIT, null, null);
        assertEquals(new LiquidationPolicy(OrderType.LIMIT, new BigDecimal("0.01"), 3), runner.getLiquidationPolicy());

        runner.updateLimits(null, null, null, null, null, null, new BigDecimal("0.02"), 5);
        assertEquals(new LiquidationPolicy(OrderType.LIMIT, new BigDecimal("0.02"), 5), runner.getLiquidationPolicy());
    }

    @Test
    void invalidLiquidationPolicyIsRejectedWithoutApplyingOtherFields() {
        StrategyRunner runner = activeRunner();
        BigDecimal allocationBefore = runner.getMaxAllocationPercent();

        assertThrows(IllegalArgumentException.class,
                () -> runner.updateLimits(new BigDecimal("0.40"), null, null, null, null, OrderType.STOP_LOSS, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> runner.updateLimits(new BigDecimal("0.40"), null, null, null, null, null, BigDecimal.ONE, null));
        assertThrows(IllegalArgumentException.class,
                () -> runner.updateLimits(new BigDecimal("0.40"), null, null, null, null, null, null, 0));
        assertEquals(0, allocationBefore.compareTo(runner.getMaxAllocationPercent()),
                "Pedido invalido nao aplica os outros campos");
        assertEquals(LiquidationPolicy.defaults(), runner.getLiquidationPolicy());
    }

    @Test
    void invalidLotProtectionIsRejectedWithoutApplyingOtherFields() {
        StrategyRunner runner = activeRunner();
        BigDecimal allocationBefore = runner.getMaxAllocationPercent();

        assertThrows(IllegalArgumentException.class,
                () -> runner.updateLimits(new BigDecimal("0.40"), null, null, BigDecimal.ONE, null, null, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> runner.updateLimits(null, null, null, BigDecimal.ZERO, null, null, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> runner.updateLimits(null, null, null, null, Duration.ofMillis(500), null, null, null));
        assertEquals(0, allocationBefore.compareTo(runner.getMaxAllocationPercent()),
                "Pedido invalido nao aplica os outros campos");
    }

    private static StrategyRunner activeRunner() {
        StrategyRunner runner = newRunner();
        runner.startInitializing(RunnerStatusCause.manual("test"));
        runner.activate(RunnerStatusCause.manual("test"));
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
