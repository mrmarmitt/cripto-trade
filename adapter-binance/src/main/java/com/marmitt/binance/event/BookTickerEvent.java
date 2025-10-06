package com.marmitt.binance.event;

import java.math.BigDecimal;

public record BookTickerEvent(
        Long u,                      // Order book updateId
        String s,                    // Symbol
        String b,                    // Best bid price
        String B,                    // Best bid qty
        String a,                    // Best ask price
        String A                     // Best ask qty
) {
    public BigDecimal getBestBidPriceAsDecimal() {
        return new BigDecimal(b);
    }

    public BigDecimal getBestBidQuantityAsDecimal() {
        return new BigDecimal(B);
    }

    public BigDecimal getBestAskPriceAsDecimal() {
        return new BigDecimal(a);
    }

    public BigDecimal getBestAskQuantityAsDecimal() {
        return new BigDecimal(A);
    }
}