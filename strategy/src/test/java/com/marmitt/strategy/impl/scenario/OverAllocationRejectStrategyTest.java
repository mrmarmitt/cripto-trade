package com.marmitt.strategy.impl.scenario;

import com.marmitt.core.dto.strategy.StrategyOutputDto;
import com.marmitt.core.enums.TradingAction;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static com.marmitt.strategy.impl.scenario.ScenarioTestFixtures.context;
import static com.marmitt.strategy.impl.scenario.ScenarioTestFixtures.input;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OverAllocationRejectStrategyTest {

    private static final BigDecimal MARKET = new BigDecimal("65000");

    private final OverAllocationRejectStrategy strategy = new OverAllocationRejectStrategy();

    @Test
    void emitsBuyWhoseCostExceedsAvailableCapital() {
        BigDecimal available = new BigDecimal("1000");
        StrategyOutputDto out = strategy.executeStrategy(
                input(MARKET), context(available, List.of(), List.of()));

        assertEquals(TradingAction.SHOULD_BUY, out.decision());
        BigDecimal estimatedCost = out.quantity().multiply(MARKET);
        assertTrue(estimatedCost.compareTo(available) > 0,
                "custo estimado (" + estimatedCost + ") deve exceder o capital disponivel (" + available + ")");
    }

    @Test
    void fallsBackToPositiveQuantityWhenNoCapital() {
        StrategyOutputDto out = strategy.executeStrategy(
                input(MARKET), context(BigDecimal.ZERO, List.of(), List.of()));

        assertEquals(TradingAction.SHOULD_BUY, out.decision());
        assertTrue(out.quantity().compareTo(BigDecimal.ZERO) > 0, "quantidade deve ser positiva");
    }
}
