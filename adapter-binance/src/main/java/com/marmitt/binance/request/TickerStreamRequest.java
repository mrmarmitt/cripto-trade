package com.marmitt.binance.request;

import java.util.List;
import java.util.Map;

public record TickerStreamRequest(
        String id,
        String method,
        Map<String, Object> params
) {
    public static TickerStreamRequest subscribe(String id, List<String> symbols) {
        return new TickerStreamRequest(
                id,
                "SUBSCRIBE",
                Map.of("params", symbols.stream()
                        .map(symbol -> symbol.toLowerCase() + "@ticker")
                        .toList())
        );
    }

    public static TickerStreamRequest unsubscribe(String id, List<String> symbols) {
        return new TickerStreamRequest(
                id,
                "UNSUBSCRIBE",
                Map.of("params", symbols.stream()
                        .map(symbol -> symbol.toLowerCase() + "@ticker")
                        .toList())
        );
    }
}