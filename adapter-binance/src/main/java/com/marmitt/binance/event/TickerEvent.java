package com.marmitt.binance.event;

import java.math.BigDecimal;

public record TickerEvent(
        String e,                    // Event type
        Long E,                      // Event time
        String s,                    // Symbol
        String p,                    // Price change
        String P,                    // Price change percent
        String w,                    // Weighted average price
        String x,                    // First trade(F)-1 price (first trade before the 24hr rolling window)
        String c,                    // Last price
        String Q,                    // Last quantity
        String b,                    // Best bid price
        String B,                    // Best bid quantity
        String a,                    // Best ask price
        String A,                    // Best ask quantity
        String o,                    // Open price
        String h,                    // High price
        String l,                    // Low price
        String v,                    // Total traded base asset volume
        String q,                    // Total traded quote asset volume
        Long O,                      // Statistics open time
        Long C,                      // Statistics close time
        Long F,                      // First trade ID
        Long L,                      // Last trade ID
        Long n                       // Total number of trades
) {
    public BigDecimal getLastPriceAsDecimal() {
        return new BigDecimal(c);
    }

    public BigDecimal getBestBidPriceAsDecimal() {
        return new BigDecimal(b);
    }

    public BigDecimal getBestAskPriceAsDecimal() {
        return new BigDecimal(a);
    }
}