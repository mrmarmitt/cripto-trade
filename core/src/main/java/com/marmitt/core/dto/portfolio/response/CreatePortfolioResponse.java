package com.marmitt.core.dto.portfolio.response;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record CreatePortfolioResponse(
        UUID portfolioId,
        String name,
        String initialCapital,
        String currency,
        boolean isActive,
        Instant createdAt,
        String message
) {

    public static CreatePortfolioResponse success(
            UUID portfolioId,
            String name,
            String initialCapital,
            String currency,
            Instant createdAt
    ) {
        return CreatePortfolioResponse.builder()
                .portfolioId(portfolioId)
                .name(name)
                .initialCapital(initialCapital)
                .currency(currency)
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

