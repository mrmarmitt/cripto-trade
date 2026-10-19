package com.marmitt.core.dto.runner.request;

import java.math.BigDecimal;
import java.time.Duration;

/**
 * Limites mutáveis do runner (IG §13.2.2, T49) e proteção do lote (T44). Campo nulo mantém o valor
 * atual.
 *
 * @param maxLossPercentPerLot perda máxima do lote como fração em (0, 1), ex.: 0.05
 * @param maxHoldingDuration   idade máxima do lote (ISO-8601, ex.: {@code PT24H}); {@code PT0S} desliga
 */
public record UpdateRunnerLimitsRequest(
        BigDecimal maxAllocationPercent,
        Integer maxOpenPositions,
        Integer maxPendingOrders,
        BigDecimal maxLossPercentPerLot,
        Duration maxHoldingDuration
) {
}
