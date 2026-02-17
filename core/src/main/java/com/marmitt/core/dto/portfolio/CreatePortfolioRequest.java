package com.marmitt.core.dto.portfolio;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Builder
public record CreatePortfolioRequest(
        String name,
        UUID strategyId,
        String symbol,
        BigDecimal initialCapitalAmount,
        String currency,
        String exchangeName,
        Set<String> allowedMarketDataSources
) {
    public CreatePortfolioRequest {
        Objects.requireNonNull(name, "Portfolio name cannot be null");
        Objects.requireNonNull(strategyId, "Strategy ID cannot be null");
        Objects.requireNonNull(symbol, "Symbol cannot be null");
        Objects.requireNonNull(initialCapitalAmount, "Initial capital amount cannot be null");
        Objects.requireNonNull(currency, "Currency cannot be null");
        Objects.requireNonNull(exchangeName, "Exchange name cannot be null");

        if (name.isBlank()) {
            throw new IllegalArgumentException("Portfolio name cannot be blank");
        }

        if (symbol.isBlank()) {
            throw new IllegalArgumentException("Symbol cannot be blank");
        }

        if (exchangeName.isBlank()) {
            throw new IllegalArgumentException("Exchange name cannot be blank");
        }

        if (initialCapitalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Initial capital must be positive");
        }
    }
}
