package com.marmitt.core.dto.runner.request;

import lombok.Builder;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Builder
public record CreateRunnerRequest(
        UUID portfolioId,
        UUID strategyId,
        String symbol,
        String exchangeName,
        Set<String> allowedMarketDataSources
) {
    public CreateRunnerRequest {
        Objects.requireNonNull(portfolioId, "portfolioId cannot be null");
        Objects.requireNonNull(strategyId, "strategyId cannot be null");
        Objects.requireNonNull(symbol, "symbol cannot be null");
        Objects.requireNonNull(exchangeName, "exchangeName cannot be null");

        if (symbol.isBlank()) {
            throw new IllegalArgumentException("symbol cannot be blank");
        }

        if (exchangeName.isBlank()) {
            throw new IllegalArgumentException("exchangeName cannot be blank");
        }
    }
}

