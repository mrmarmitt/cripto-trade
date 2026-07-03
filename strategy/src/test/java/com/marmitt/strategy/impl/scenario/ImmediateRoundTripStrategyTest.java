package com.marmitt.strategy.impl.scenario;

import com.marmitt.core.dto.strategy.StrategyOutputDto;
import com.marmitt.core.enums.TradingAction;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static com.marmitt.strategy.impl.scenario.ScenarioTestFixtures.context;
import static com.marmitt.strategy.impl.scenario.ScenarioTestFixtures.input;
import static com.marmitt.strategy.impl.scenario.ScenarioTestFixtures.openLot;
import static com.marmitt.strategy.impl.scenario.ScenarioTestFixtures.pending;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImmediateRoundTripStrategyTest {

    private static final BigDecimal MARKET = new BigDecimal("65000");

    private final ImmediateRoundTripStrategy strategy = new ImmediateRoundTripStrategy();

    @Test
    void placesMarketableBuyWhenClean() {
        StrategyOutputDto out = strategy.executeStrategy(
                input(MARKET), context(new BigDecimal("10000"), List.of(), List.of()));

        assertEquals(TradingAction.SHOULD_BUY, out.decision());
        assertTrue(out.limitPrice().compareTo(MARKET) > 0, "BUY marketable deve estar acima do mercado");
    }

    @Test
    void placesMarketableSellToCloseLot() {
        BigDecimal lotQty = new BigDecimal("0.001");
        StrategyOutputDto out = strategy.executeStrategy(
                input(MARKET), context(new BigDecimal("10000"), List.of(openLot(lotQty)), List.of()));

        assertEquals(TradingAction.SHOULD_SELL, out.decision());
        assertTrue(out.limitPrice().compareTo(MARKET) < 0, "SELL marketable deve estar abaixo do mercado");
        assertEquals(0, out.quantity().compareTo(lotQty));
    }

    @Test
    void holdsWhilePending() {
        StrategyOutputDto out = strategy.executeStrategy(
                input(MARKET),
                context(new BigDecimal("10000"), List.of(), List.of(pending(TradingAction.SHOULD_BUY, UUID.randomUUID()))));

        assertEquals(TradingAction.SHOULD_HOLD, out.decision());
    }
}
