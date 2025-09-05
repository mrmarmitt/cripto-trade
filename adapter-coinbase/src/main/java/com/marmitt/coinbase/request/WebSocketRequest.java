package com.marmitt.coinbase.request;

import java.util.List;

public record WebSocketRequest(
        String type,
        List<String> product_ids,
        List<String> channels
) {
    public static WebSocketRequest subscribe(List<String> productIds, List<String> channels) {
        return new WebSocketRequest("subscribe", productIds, channels);
    }
    
    public static WebSocketRequest unsubscribe(List<String> productIds, List<String> channels) {
        return new WebSocketRequest("unsubscribe", productIds, channels);
    }
}