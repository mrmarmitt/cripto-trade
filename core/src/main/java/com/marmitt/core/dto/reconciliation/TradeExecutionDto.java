package com.marmitt.core.dto.reconciliation;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Generic representation of a single fill event returned by the exchange.
 * One order can produce multiple fills (partial fills); each is one TradeExecutionDto.
 */
public record TradeExecutionDto(
        String exchangeTradeId,
        long exchangeOrderId,
        String clientOrderId,
        String symbol,
        BigDecimal price,
        BigDecimal qty,
        BigDecimal quoteQty,
        BigDecimal commission,
        String commissionAsset,
        Instant executedAt,
        boolean isBuy
) {}
