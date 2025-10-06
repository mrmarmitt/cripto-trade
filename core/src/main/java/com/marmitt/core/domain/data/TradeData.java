package com.marmitt.core.domain.data;

import com.marmitt.core.domain.Symbol;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Representa dados de negociação/trade processados de mensagens WebSocket
 */
public record TradeData(
    String tradeId,
    Symbol symbol,
    BigDecimal price,
    BigDecimal quantity,
    OrderData.OrderSide side,
    String buyerOrderId,
    String sellerOrderId,
    BigDecimal fee,
    String feeAsset,
    Instant timestamp
) implements ProcessorResponse {}