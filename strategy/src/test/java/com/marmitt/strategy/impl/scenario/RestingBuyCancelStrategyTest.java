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

class RestingBuyCancelStrategyTest {

    private static final BigDecimal MARKET = new BigDecimal("65000");

    private final RestingBuyCancelStrategy strategy = new RestingBuyCancelStrategy();

    @Test
    void placesRestingBuyBelowMarketWhenClean() {
        StrategyOutputDto out = strategy.executeStrategy(
                input(MARKET), context(new BigDecimal("10000"), List.of(), List.of()));

        assertEquals(TradingAction.SHOULD_BUY, out.decision());
        assertTrue(out.limitPrice().compareTo(MARKET) < 0, "BUY deve descansar abaixo do mercado");
        assertEquals(0, out.quantity().compareTo(new BigDecimal("0.001")));
    }

    @Test
    void cancelsPendingBuy() {
        UUID txId = UUID.randomUUID();
        StrategyOutputDto out = strategy.executeStrategy(
                input(MARKET),
                context(new BigDecimal("10000"), List.of(), List.of(pending(TradingAction.SHOULD_BUY, txId))));

        assertEquals(TradingAction.SHOULD_CANCEL, out.decision());
        assertEquals(txId, out.targetTransactionId());
    }

    @Test
    void holdsWhenLotAlreadyOpen() {
        StrategyOutputDto out = strategy.executeStrategy(
                input(MARKET),
                context(new BigDecimal("10000"), List.of(openLot(new BigDecimal("0.001"))), List.of()));

        assertEquals(TradingAction.SHOULD_HOLD, out.decision());
    }
}
