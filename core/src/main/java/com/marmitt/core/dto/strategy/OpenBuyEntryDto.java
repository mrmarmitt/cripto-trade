package com.marmitt.core.dto.strategy;

import com.marmitt.core.domain.portfolio.Asset;
import com.marmitt.core.domain.portfolio.Transaction;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Representa uma compra executada que ainda compõe a posição aberta.
 * Usado pela Strategy para decisões de stop loss, timeout, etc.
 */
@Builder
public record OpenBuyEntryDto(
        UUID lotId,
        Asset executedQuantity,
        Asset executedPrice,
        Asset remainingQuantity,
        Asset reservedQuantity,
        Instant executedAt
) {
    public OpenBuyEntryDto {
        Objects.requireNonNull(lotId, "Lot ID cannot be null");
        Objects.requireNonNull(executedQuantity, "Executed quantity cannot be null");
        Objects.requireNonNull(executedPrice, "Executed price cannot be null");
        Objects.requireNonNull(remainingQuantity, "Remaining quantity cannot be null");
        Objects.requireNonNull(reservedQuantity, "Reserved quantity cannot be null");
        Objects.requireNonNull(executedAt, "Executed at cannot be null");
    }

    public static OpenBuyEntryDto fromTransaction(Transaction tx, BigDecimal freeAmount, BigDecimal reservedAmount) {
        Asset free = Asset.of(freeAmount, tx.executedQuantity().currency());
        Asset reserved = Asset.of(reservedAmount, tx.executedQuantity().currency());
        return OpenBuyEntryDto.builder()
                .lotId(tx.id())
                .executedQuantity(tx.executedQuantity())
                .executedPrice(tx.executedPrice())
                .remainingQuantity(free)
                .reservedQuantity(reserved)
                .executedAt(tx.executedAt())
                .build();
    }
}
