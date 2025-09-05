package com.marmitt.core.dto.trade;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

public record TradeResponse(
        String requestId,
        String orderId,
        String symbol,
        String side,
        String status,
        BigDecimal originalQuantity,
        BigDecimal executedQuantity,
        BigDecimal price,
        BigDecimal averagePrice,
        String orderType,
        Instant timestamp,
        Map<String, Object> additionalData
) {
    public static TradeResponse success(String requestId, String orderId, String symbol, String side,
                                        BigDecimal quantity, BigDecimal price, Instant timestamp) {
        return new TradeResponse(requestId, orderId, symbol, side, "FILLED", quantity, quantity, 
                                price, price, "LIMIT", timestamp, Map.of());
    }
    
    public static TradeResponse partialFill(String requestId, String orderId, String symbol, String side,
                                           BigDecimal originalQty, BigDecimal executedQty, BigDecimal avgPrice, Instant timestamp) {
        return new TradeResponse(requestId, orderId, symbol, side, "PARTIALLY_FILLED", originalQty, 
                                executedQty, null, avgPrice, "LIMIT", timestamp, Map.of());
    }
    
    public static TradeResponse pending(String requestId, String orderId, String symbol, String side,
                                       BigDecimal quantity, BigDecimal price, Instant timestamp) {
        return new TradeResponse(requestId, orderId, symbol, side, "NEW", quantity, BigDecimal.ZERO, 
                                price, null, "LIMIT", timestamp, Map.of());
    }
    
    public static TradeResponse cancelled(String requestId, String orderId, String symbol, Instant timestamp) {
        return new TradeResponse(requestId, orderId, symbol, null, "CANCELLED", null, null, 
                                null, null, null, timestamp, Map.of());
    }
    
    public static TradeResponse error(String requestId, String errorMessage, Instant timestamp) {
        return new TradeResponse(requestId, null, null, null, "ERROR", null, null, 
                                null, null, null, timestamp, Map.of("error", errorMessage));
    }
}