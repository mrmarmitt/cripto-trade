package com.marmitt.core.dto.strategy;

import com.marmitt.core.enums.TradingAction;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cobre o campo opcional {@code limitPrice} (T34): factories com preço explícito o preenchem,
 * factories sem preço mantêm {@code null} (usa preço de mercado do tick), e o preço deve ser positivo.
 */
class StrategyOutputDtoLimitPriceTest {

    @Test
    void buyAtSetsLimitPriceAndBuyDecision() {
        BigDecimal limit = new BigDecimal("64000.00");
        StrategyOutputDto out = StrategyOutputDto.buyAt("scn", new BigDecimal("0.8"),
                new BigDecimal("0.01"), limit, "resting buy");

        assertEquals(TradingAction.SHOULD_BUY, out.decision());
        assertEquals(0, limit.compareTo(out.limitPrice()));
        assertTrue(out.shouldTrade());
    }

    @Test
    void sellAtSetsLimitPriceAndSellDecision() {
        BigDecimal limit = new BigDecimal("70000.00");
        StrategyOutputDto out = StrategyOutputDto.sellAt("scn", new BigDecimal("1.0"),
                new BigDecimal("0.01"), limit, "resting sell");

        assertEquals(TradingAction.SHOULD_SELL, out.decision());
        assertEquals(0, limit.compareTo(out.limitPrice()));
        assertTrue(out.shouldTrade());
    }

    @Test
    void priceLessFactoriesLeaveLimitPriceNull() {
        assertNull(StrategyOutputDto.buy("scn", new BigDecimal("0.8"), new BigDecimal("0.01"), "x").limitPrice());
        assertNull(StrategyOutputDto.sell("scn", new BigDecimal("0.8"), new BigDecimal("0.01"), "x").limitPrice());
        assertNull(StrategyOutputDto.sellLot("scn", BigDecimal.ONE, new BigDecimal("0.01"), UUID.randomUUID(), "x").limitPrice());
        assertNull(StrategyOutputDto.hold("scn", "x").limitPrice());
        assertNull(StrategyOutputDto.cancel("scn", UUID.randomUUID(), "x").limitPrice());
    }

    @Test
    void buyAtRejectsNonPositiveLimitPrice() {
        assertThrows(IllegalArgumentException.class, () -> StrategyOutputDto.buyAt(
                "scn", new BigDecimal("0.8"), new BigDecimal("0.01"), BigDecimal.ZERO, "x"));
        assertThrows(IllegalArgumentException.class, () -> StrategyOutputDto.buyAt(
                "scn", new BigDecimal("0.8"), new BigDecimal("0.01"), new BigDecimal("-1"), "x"));
        assertThrows(IllegalArgumentException.class, () -> StrategyOutputDto.buyAt(
                "scn", new BigDecimal("0.8"), new BigDecimal("0.01"), null, "x"));
    }

    @Test
    void sellAtRejectsNonPositiveLimitPrice() {
        assertThrows(IllegalArgumentException.class, () -> StrategyOutputDto.sellAt(
                "scn", new BigDecimal("1.0"), new BigDecimal("0.01"), BigDecimal.ZERO, "x"));
        assertThrows(IllegalArgumentException.class, () -> StrategyOutputDto.sellAt(
                "scn", new BigDecimal("1.0"), new BigDecimal("0.01"), null, "x"));
    }
}
