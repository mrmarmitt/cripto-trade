package com.marmitt.coinbase.request;

import java.util.List;

public record TickerStreamRequest(
        String type,
        List<String> product_ids,
        List<String> channels
) {
    public static TickerStreamRequest subscribe(List<String> productIds) {
        return new TickerStreamRequest("subscribe", productIds, List.of("ticker"));
    }
}