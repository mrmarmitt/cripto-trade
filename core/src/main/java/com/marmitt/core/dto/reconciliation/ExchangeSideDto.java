package com.marmitt.core.dto.reconciliation;

import java.math.BigDecimal;
import java.time.Instant;

/** Aggregated view of all fills for one order on the exchange side. */
public record ExchangeSideDto(
        long exchangeOrderId,
        int fillCount,
        BigDecimal executedQty,
        BigDecimal avgPrice,
        BigDecimal totalQuote,
        BigDecimal totalFees,
        String feeAsset,
        Instant firstFillAt,
        Instant lastFillAt
) {}
