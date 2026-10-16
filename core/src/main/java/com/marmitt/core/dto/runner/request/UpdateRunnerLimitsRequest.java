package com.marmitt.core.dto.runner.request;

import java.math.BigDecimal;

/**
 * Limites mutáveis do runner (IG §13.2.2, T49). Campo nulo mantém o valor atual.
 */
public record UpdateRunnerLimitsRequest(
        BigDecimal maxAllocationPercent,
        Integer maxOpenPositions,
        Integer maxPendingOrders
) {
}
