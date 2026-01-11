package com.marmitt.core.dto.portfolio;

import com.marmitt.core.domain.Symbol;
import com.marmitt.core.domain.portfolio.Asset;
import lombok.Builder;

import java.util.Objects;
import java.util.UUID;

@Builder
public record CreatePortfolioRequest(
        String name,
        UUID strategyId,
        String strategyName,
        Symbol symbol,
        Asset initialCapital,
        String exchangeName
) {
    public CreatePortfolioRequest {
        Objects.requireNonNull(name, "Portfolio name cannot be null");
        Objects.requireNonNull(strategyId, "Strategy ID cannot be null");
        Objects.requireNonNull(strategyName, "Strategy name cannot be null");
        Objects.requireNonNull(symbol, "Symbol cannot be null");
        Objects.requireNonNull(initialCapital, "Initial capital cannot be null");
        Objects.requireNonNull(exchangeName, "Exchange name cannot be null");

        if (name.isBlank()) {
            throw new IllegalArgumentException("Portfolio name cannot be blank");
        }

        if (strategyName.isBlank()) {
            throw new IllegalArgumentException("Strategy name cannot be blank");
        }

        if (exchangeName.isBlank()) {
            throw new IllegalArgumentException("Exchange name cannot be blank");
        }

        if (!initialCapital.isPositive()) {
            throw new IllegalArgumentException("Initial capital must be positive");
        }
    }
}
