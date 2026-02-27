package com.marmitt.core.dto.portfolio;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.UUID;

@Builder
public record PortfolioPnlDto(
        UUID portfolioId,
        BigDecimal realizedPnL,
        BigDecimal unrealizedPnL,
        BigDecimal totalPnL
) {
}
