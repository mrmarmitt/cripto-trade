package com.marmitt.core.dto.portfolio.request;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.Objects;

@Builder
public record CreatePortfolioRequest(
        String name,
        BigDecimal initialCapitalAmount,
        String currency
) {
    public CreatePortfolioRequest {
        Objects.requireNonNull(name, "Portfolio name cannot be null");
        Objects.requireNonNull(initialCapitalAmount, "Initial capital amount cannot be null");
        Objects.requireNonNull(currency, "Currency cannot be null");

        if (name.isBlank()) {
            throw new IllegalArgumentException("Portfolio name cannot be blank");
        }

        if (currency.isBlank()) {
            throw new IllegalArgumentException("Currency cannot be blank");
        }

        if (initialCapitalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Initial capital must be positive");
        }
    }
}

