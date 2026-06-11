package com.marmitt.core.dto.reconciliation;

import com.marmitt.core.enums.TransactionStatus;
import com.marmitt.core.enums.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Local transaction data for one side of a reconciliation entry. */
public record LocalSideDto(
        UUID transactionId,
        UUID runnerId,
        TransactionType type,
        TransactionStatus status,
        BigDecimal executedQty,
        BigDecimal executedPrice,
        BigDecimal total,
        Instant requestedAt,
        Instant updatedAt
) {}
