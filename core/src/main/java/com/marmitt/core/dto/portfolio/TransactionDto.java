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
                .id(transaction.getId())
                .clientOrderId(transaction.getClientOrderId())
                .status(transaction.getStatus())
                .type(transaction.getType())
                .symbol(transaction.getSymbol().value())
                .quantity(transaction.getQuantity().amount())
                .quantityCurrency(transaction.getQuantity().currency())
                .price(transaction.getPrice().amount())
                .priceCurrency(transaction.getPrice().currency())
                .total(transaction.getTotal().amount())
                .executedQuantity(transaction.getExecutedQuantity() != null ?
                        transaction.getExecutedQuantity().amount() : null)
                .executedPrice(transaction.getExecutedPrice() != null ?
                        transaction.getExecutedPrice().amount() : null)
                .fee(transaction.getFee() != null ? transaction.getFee().amount() : null)
                .requestedAt(transaction.getRequestedAt())
                .executedAt(transaction.getExecutedAt())
                .rejectReason(transaction.getRejectReason())
                .build();
    }
}
