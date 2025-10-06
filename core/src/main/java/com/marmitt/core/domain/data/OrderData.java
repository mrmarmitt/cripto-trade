package com.marmitt.core.domain.data;

import com.marmitt.core.domain.Symbol;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Representa dados de uma ordem processada de mensagens WebSocket
 */
public record OrderData(
    String orderId,
    Symbol symbol,
    OrderSide side,
    OrderType type,
    BigDecimal quantity,
    BigDecimal price,
    OrderStatus status,
    Instant timestamp
) implements ProcessorResponse {

    public enum OrderSide {
        BUY, SELL
    }
    
    public enum OrderType {
        MARKET, LIMIT, STOP, STOP_LIMIT
    }
    
    public enum OrderStatus {
        NEW, PARTIALLY_FILLED, FILLED, CANCELED, REJECTED, EXPIRED
    }
}