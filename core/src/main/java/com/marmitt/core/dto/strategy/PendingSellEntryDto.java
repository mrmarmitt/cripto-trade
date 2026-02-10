package com.marmitt.core.dto.strategy;

import com.marmitt.core.domain.portfolio.Asset;
import com.marmitt.core.domain.portfolio.Transaction;
import com.marmitt.core.enums.TransactionStatus;
import lombok.Builder;

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
        Asset quantity,
        Asset price,
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

    public static PendingSellEntryDto fromTransaction(Transaction tx) {
        return PendingSellEntryDto.builder()
                .transactionId(tx.id())
                .quantity(tx.quantity())
                .price(tx.price())
                .status(tx.status())
                .requestedAt(tx.requestedAt())
                .build();
    }
}
