package com.marmitt.core.domain.runner;

import com.marmitt.core.enums.OrderType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T48: politica de liquidacao do PANIC_SELL por runner.
 */
class LiquidationPolicyTest {

    @Test
    void defaultsKeepTheMarketExitWithTheDecidedLimitParameters() {
        LiquidationPolicy policy = LiquidationPolicy.defaults();

        assertEquals(OrderType.MARKET, policy.orderType());
        assertFalse(policy.usesLimit());
        assertEquals(0, new BigDecimal("0.01").compareTo(policy.maxSlippagePercent()));
        assertEquals(3, policy.escalationTicks());
    }

    @Test
    void limitPriceIsTheTickPriceMinusTheSlippage() {
        LiquidationPolicy policy = new LiquidationPolicy(OrderType.LIMIT, new BigDecimal("0.01"), 3);

        assertTrue(policy.usesLimit());
        assertEquals(0, new BigDecimal("64350").compareTo(policy.limitPrice(new BigDecimal("65000"))));
    }

    @Test
    void rejectsInvalidParameters() {
        assertThrows(IllegalArgumentException.class,
                () -> new LiquidationPolicy(OrderType.STOP_LOSS, new BigDecimal("0.01"), 3));
        assertThrows(IllegalArgumentException.class,
                () -> new LiquidationPolicy(OrderType.LIMIT, BigDecimal.ZERO, 3));
        assertThrows(IllegalArgumentException.class,
                () -> new LiquidationPolicy(OrderType.LIMIT, BigDecimal.ONE, 3));
        assertThrows(IllegalArgumentException.class,
                () -> new LiquidationPolicy(OrderType.LIMIT, new BigDecimal("0.01"), 0));
        assertThrows(NullPointerException.class,
                () -> new LiquidationPolicy(null, new BigDecimal("0.01"), 3));
    }
}
