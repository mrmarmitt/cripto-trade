package com.marmitt.core.dto.portfolio;

import com.marmitt.core.domain.portfolio.Transaction;
import com.marmitt.core.enums.TransactionStatus;
import com.marmitt.core.enums.TransactionType;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Builder
public record TransactionDto(
        UUID id,
        String clientOrderId,
        TransactionStatus status,
        TransactionType type,
        String symbol,

        // Valores solicitados
        BigDecimal quantity,
        String quantityCurrency,
        BigDecimal price,
        String priceCurrency,
        BigDecimal total,

        // Valores executados (pode ser diferente do solicitado)
        BigDecimal executedQuantity,
        BigDecimal executedPrice,
        BigDecimal fee,

        // Timestamps
        Instant requestedAt,
        Instant executedAt,

        // Rejeição
        String rejectReason
) {

    public static TransactionDto fromDomain(Transaction transaction) {
        return TransactionDto.builder()
                .id(transaction.id())
                .clientOrderId(transaction.clientOrderId())
                .status(transaction.status())
                .type(transaction.type())
                .symbol(transaction.symbol().value())
                .quantity(transaction.quantity().amount())
                .quantityCurrency(transaction.quantity().currency())
                .price(transaction.price().amount())
                .priceCurrency(transaction.price().currency())
                .total(transaction.total().amount())
                .executedQuantity(transaction.executedQuantity() != null ?
                        transaction.executedQuantity().amount() : null)
                .executedPrice(transaction.executedPrice() != null ?
                        transaction.executedPrice().amount() : null)
                .fee(transaction.fee() != null ? transaction.fee().amount() : null)
                .requestedAt(transaction.requestedAt())
                .executedAt(transaction.executedAt())
                .rejectReason(transaction.rejectReason())
                .build();
    }
}
