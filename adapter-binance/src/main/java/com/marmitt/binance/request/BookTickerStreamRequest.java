package com.marmitt.binance.request;

import java.util.List;
import java.util.Map;

public record BookTickerStreamRequest(
        String id,
        String method,
        Map<String, Object> params
) {
    public static BookTickerStreamRequest subscribe(String id, List<String> symbols) {
        return new BookTickerStreamRequest(
                id,
                "SUBSCRIBE",
                Map.of("params", symbols.stream()
                        .map(symbol -> symbol.toLowerCase() + "@bookTicker")
                        .toList())
        );
    }

    public static BookTickerStreamRequest unsubscribe(String id, List<String> symbols) {
        return new BookTickerStreamRequest(
                id,
                "UNSUBSCRIBE",
                Map.of("params", symbols.stream()
                        .map(symbol -> symbol.toLowerCase() + "@bookTicker")
                        .toList())
        );
    }
}