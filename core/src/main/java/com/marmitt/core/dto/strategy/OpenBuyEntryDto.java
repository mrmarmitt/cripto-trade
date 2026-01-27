package com.marmitt.core.dto.strategy;

import com.marmitt.core.domain.portfolio.Asset;
import com.marmitt.core.domain.portfolio.Transaction;
import lombok.Builder;

import java.time.Instant;
import java.util.Objects;

/**
 * Representa uma compra executada que ainda compõe a posição aberta.
 * Usado pela Strategy para decisões de stop loss, timeout, etc.
 */
@Builder
public record OpenBuyEntryDto(
        Asset executedQuantity,
        Asset executedPrice,
        Instant executedAt
) {
    public OpenBuyEntryDto {
        Objects.requireNonNull(executedQuantity, "Executed quantity cannot be null");
        Objects.requireNonNull(executedPrice, "Executed price cannot be null");
        Objects.requireNonNull(executedAt, "Executed at cannot be null");
    }

    public static OpenBuyEntryDto fromTransaction(Transaction transaction) {
        return OpenBuyEntryDto.builder()
                .executedQuantity(transaction.executedQuantity())
                .executedPrice(transaction.executedPrice())
                .executedAt(transaction.executedAt())
                .build();
    }
}
