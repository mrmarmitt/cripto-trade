package com.marmitt.core.dto.strategy;

import com.marmitt.core.enums.TransactionStatus;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Representa um SELL em trânsito (PENDING ou SUBMITTED).
 * Permite que a Strategy veja quais ordens de venda estão aguardando execução.
 */
@Builder
public record PendingSellEntryDto(
        UUID transactionId,
        BigDecimal quantity,
        BigDecimal price,
        TransactionStatus status,
        Instant requestedAt
) {
    public PendingSellEntryDto {
        Objects.requireNonNull(transactionId, "Transaction ID cannot be null");
        Objects.requireNonNull(quantity, "Quantity cannot be null");
        Objects.requireNonNull(price, "Price cannot be null");
        Objects.requireNonNull(status, "Status cannot be null");
        Objects.requireNonNull(requestedAt, "RequestedAt cannot be null");
    }
}
