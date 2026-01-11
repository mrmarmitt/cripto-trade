package com.marmitt.core.dto.portfolio;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record CreatePortfolioResponse(
        UUID portfolioId,
        String name,
        UUID strategyId,
        String strategyName,
        String symbol,
        String initialCapital,
        String currency,
        String exchangeName,
        boolean isActive,
        Instant createdAt,
        String message
) {

    public static CreatePortfolioResponse success(
            UUID portfolioId,
            String name,
            UUID strategyId,
            String strategyName,
            String symbol,
            String initialCapital,
            String currency,
            String exchangeName,
            Instant createdAt
    ) {
        return CreatePortfolioResponse.builder()
                .portfolioId(portfolioId)
                .name(name)
                .strategyId(strategyId)
                .strategyName(strategyName)
                .symbol(symbol)
                .initialCapital(initialCapital)
                .currency(currency)
                .exchangeName(exchangeName)
                .isActive(true)
                .createdAt(createdAt)
                .message("Portfolio created successfully")
                .build();
    }

    public static CreatePortfolioResponse failure(String message) {
        return CreatePortfolioResponse.builder()
                .message(message)
                .build();
    }
}
