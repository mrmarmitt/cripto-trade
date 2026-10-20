package com.marmitt.core.dto.runner.request;

import com.marmitt.core.enums.OrderType;

import java.math.BigDecimal;
import java.time.Duration;

/**
 * Limites mutáveis do runner (IG §13.2.2, T49), proteção do lote (T44) e política de liquidação do
 * PANIC_SELL (T48). Campo nulo mantém o valor atual.
 *
 * @param maxLossPercentPerLot          perda máxima do lote como fração em (0, 1), ex.: 0.05
 * @param maxHoldingDuration            idade máxima do lote (ISO-8601, ex.: {@code PT24H}); {@code PT0S} desliga
 * @param liquidationOrderType          {@code MARKET} ou {@code LIMIT}
 * @param liquidationMaxSlippagePercent afastamento da SELL LIMIT abaixo do último preço, fração em (0, 1)
 * @param liquidationEscalationTicks    ticks sem preenchimento antes de escalar para MARKET (≥ 1)
 */
public record UpdateRunnerLimitsRequest(
        BigDecimal maxAllocationPercent,
        Integer maxOpenPositions,
        Integer maxPendingOrders,
        BigDecimal maxLossPercentPerLot,
        Duration maxHoldingDuration,
        OrderType liquidationOrderType,
        BigDecimal liquidationMaxSlippagePercent,
        Integer liquidationEscalationTicks
) {
}
