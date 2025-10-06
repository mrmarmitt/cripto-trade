package com.marmitt.binance.request;

import java.util.List;
import java.util.Map;

public record DepthStreamRequest(
        String id,
        String method,
        Map<String, Object> params
) {
    public static DepthStreamRequest subscribe(String id, List<String> symbols, String levels) {
        return new DepthStreamRequest(
                id,
                "SUBSCRIBE",
                Map.of("params", symbols.stream()
                        .map(symbol -> symbol.toLowerCase() + "@depth" + levels)
                        .toList())
        );
    }

    public static DepthStreamRequest subscribe(String id, List<String> symbols) {
        return subscribe(id, symbols, "20");
    }

    public static DepthStreamRequest unsubscribe(String id, List<String> symbols, String levels) {
        return new DepthStreamRequest(
                id,
                "UNSUBSCRIBE",
                Map.of("params", symbols.stream()
                        .map(symbol -> symbol.toLowerCase() + "@depth" + levels)
                        .toList())
        );
    }

    public static DepthStreamRequest unsubscribe(String id, List<String> symbols) {
        return unsubscribe(id, symbols, "20");
    }
}