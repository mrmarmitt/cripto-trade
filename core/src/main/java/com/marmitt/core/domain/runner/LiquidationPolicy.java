package com.marmitt.core.domain.runner;

import com.marmitt.core.enums.OrderType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Como o runner liquida os próprios lotes no Safe Mode {@code PANIC_SELL} (T48). O <i>quando</i> é do
 * portfólio; o <i>como</i> é do runner, porque a liquidez varia por símbolo.
 *
 * <ul>
 *   <li>{@code MARKET} (default, comportamento da T45): saída garantida, sem controle de preço.</li>
 *   <li>{@code LIMIT}: SELL LIMIT do lote a {@code preço do tick × (1 − maxSlippagePercent)}; sem
 *       preenchimento em {@code escalationTicks} ticks, cancela e o restante do lote sai a MARKET.</li>
 * </ul>
 * {@code maxSlippagePercent} e {@code escalationTicks} são guardados também em MARKET, para que trocar
 * o tipo não perca a configuração.
 */
public record LiquidationPolicy(OrderType orderType, BigDecimal maxSlippagePercent, int escalationTicks) {

    /** Decisão de negócio da T48: afastamento de 1% do último preço. */
    public static final BigDecimal DEFAULT_MAX_SLIPPAGE_PERCENT = new BigDecimal("0.01");
    /** Decisão de negócio da T48: 3 ticks sem preenchimento antes de escalar para MARKET. */
    public static final int DEFAULT_ESCALATION_TICKS = 3;

    public LiquidationPolicy {
        Objects.requireNonNull(orderType, "orderType cannot be null");
        if (orderType != OrderType.MARKET && orderType != OrderType.LIMIT) {
            throw new IllegalArgumentException("liquidation orderType must be MARKET or LIMIT: " + orderType);
        }
        Objects.requireNonNull(maxSlippagePercent, "maxSlippagePercent cannot be null");
        if (maxSlippagePercent.signum() <= 0 || maxSlippagePercent.compareTo(BigDecimal.ONE) >= 0) {
            throw new IllegalArgumentException("maxSlippagePercent must be in (0, 1): " + maxSlippagePercent);
        }
        if (escalationTicks < 1) {
            throw new IllegalArgumentException("escalationTicks must be at least 1: " + escalationTicks);
        }
    }

    public static LiquidationPolicy defaults() {
        return new LiquidationPolicy(OrderType.MARKET, DEFAULT_MAX_SLIPPAGE_PERCENT, DEFAULT_ESCALATION_TICKS);
    }

    public boolean usesLimit() {
        return orderType == OrderType.LIMIT;
    }

    /**
     * Preço da SELL LIMIT de liquidação: {@code tickPrice × (1 − maxSlippagePercent)}. A normalização ao
     * tick size da exchange (SELL arredonda para cima, T37) fica com o pipeline de SELL.
     */
    public BigDecimal limitPrice(BigDecimal tickPrice) {
        return tickPrice.multiply(BigDecimal.ONE.subtract(maxSlippagePercent)).setScale(8, RoundingMode.HALF_UP);
    }
}
