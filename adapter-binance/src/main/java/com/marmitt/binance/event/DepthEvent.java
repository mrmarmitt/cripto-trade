package com.marmitt.binance.event;

import java.math.BigDecimal;
import java.util.List;

public record DepthEvent(
        String e,                    // Event type
        Long E,                      // Event time
        String s,                    // Symbol
        Long U,                      // First update ID in event
        Long u,                      // Final update ID in event
        List<List<String>> b,        // Bids to be updated
        List<List<String>> a         // Asks to be updated
) {
    public record PriceLevel(
            BigDecimal price,
            BigDecimal quantity
    ) {}

    public List<PriceLevel> getBidLevels() {
        return b.stream()
                .map(bid -> new PriceLevel(
                        new BigDecimal(bid.get(0)),
                        new BigDecimal(bid.get(1))
                ))
                .toList();
    }

    public List<PriceLevel> getAskLevels() {
        return a.stream()
                .map(ask -> new PriceLevel(
                        new BigDecimal(ask.get(0)),
                        new BigDecimal(ask.get(1))
                ))
                .toList();
    }
}