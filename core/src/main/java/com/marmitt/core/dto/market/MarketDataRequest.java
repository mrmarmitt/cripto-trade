package com.marmitt.core.dto.market;

import java.util.List;
import java.util.Map;

public record MarketDataRequest(
        String requestId,
        String action,
        List<String> symbols,
        String streamType,
        Map<String, Object> parameters
) {
    public static MarketDataRequest subscribe(String requestId, List<String> symbols, String streamType) {
        return new MarketDataRequest(requestId, "SUBSCRIBE", symbols, streamType, Map.of());
    }
    
    public static MarketDataRequest unsubscribe(String requestId, List<String> symbols, String streamType) {
        return new MarketDataRequest(requestId, "UNSUBSCRIBE", symbols, streamType, Map.of());
    }
    
    public static MarketDataRequest withParameters(String requestId, String action, List<String> symbols, 
                                                   String streamType, Map<String, Object> parameters) {
        return new MarketDataRequest(requestId, action, symbols, streamType, parameters);
    }
}