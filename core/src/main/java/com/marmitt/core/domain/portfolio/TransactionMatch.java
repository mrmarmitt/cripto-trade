package com.marmitt.core.domain.portfolio;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

public record TransactionMatch(
        UUID buyTransactionId,
        UUID sellTransactionId,
        BigDecimal matchedQuantity
) {
    public TransactionMatch {
        Objects.requireNonNull(buyTransactionId, "Buy transaction ID cannot be null");
        Objects.requireNonNull(sellTransactionId, "Sell transaction ID cannot be null");
        Objects.requireNonNull(matchedQuantity, "Matched quantity cannot be null");

        if (matchedQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Matched quantity must be positive");
        }
    }
}
