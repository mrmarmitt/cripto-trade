package com.marmitt.core.dto.strategy;

import com.marmitt.core.enums.TradingAction;
import com.marmitt.core.enums.TransactionStatus;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Representa uma ordem em transito (BUY ou SELL) com status PENDING/SUBMITTED/PARTIAL.
 */
@Builder
public record PendingOrderDto(
        UUID transactionId,
        TradingAction type,
        BigDecimal quantity,
        BigDecimal price,
        TransactionStatus status,
        Instant requestedAt
) {
    public PendingOrderDto {
        Objects.requireNonNull(transactionId, "Transaction ID cannot be null");
        Objects.requireNonNull(type, "Type cannot be null");
        Objects.requireNonNull(quantity, "Quantity cannot be null");
        Objects.requireNonNull(price, "Price cannot be null");
        Objects.requireNonNull(status, "Status cannot be null");
        Objects.requireNonNull(requestedAt, "RequestedAt cannot be null");
    }
}
