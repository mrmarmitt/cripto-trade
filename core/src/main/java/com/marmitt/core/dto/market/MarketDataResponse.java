package com.marmitt.core.dto.market;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

public record MarketDataResponse(
        String requestId,
        String symbol,
        String dataType,
        BigDecimal price,
        BigDecimal quantity,
        BigDecimal bidPrice,
        BigDecimal askPrice,
        BigDecimal bidQuantity,
        BigDecimal askQuantity,
        Instant timestamp,
        Map<String, Object> additionalData
) {
    public static MarketDataResponse ticker(String requestId, String symbol, BigDecimal price, Instant timestamp) {
        return new MarketDataResponse(requestId, symbol, "TICKER", price, null, null, null, null, null, timestamp, Map.of());
    }
    
    public static MarketDataResponse bookTicker(String requestId, String symbol, BigDecimal bidPrice, BigDecimal askPrice,
                                                BigDecimal bidQty, BigDecimal askQty, Instant timestamp) {
        return new MarketDataResponse(requestId, symbol, "BOOK_TICKER", null, null, bidPrice, askPrice, bidQty, askQty, timestamp, Map.of());
    }
    
    public static MarketDataResponse trade(String requestId, String symbol, BigDecimal price, BigDecimal quantity, Instant timestamp) {
        return new MarketDataResponse(requestId, symbol, "TRADE", price, quantity, null, null, null, null, timestamp, Map.of());
    }
    
    public static MarketDataResponse withAdditionalData(String requestId, String symbol, String dataType,
                                                        Map<String, Object> additionalData, Instant timestamp) {
        return new MarketDataResponse(requestId, symbol, dataType, null, null, null, null, null, null, timestamp, additionalData);
    }
}