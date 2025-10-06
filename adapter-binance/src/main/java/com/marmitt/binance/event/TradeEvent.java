package com.marmitt.binance.event;

import java.math.BigDecimal;

public record TradeEvent(
        String e,                    // Event type
        Long E,                      // Event time
        String s,                    // Symbol
        Long t,                      // Trade ID
        String p,                    // Price
        String q,                    // Quantity
        Long b,                      // Buyer order ID
        Long a,                      // Seller order ID
        Long T,                      // Trade time
        Boolean m,                   // Is the buyer the market maker?
        Boolean M                    // Ignore
) {
    public BigDecimal getPriceAsDecimal() {
        return new BigDecimal(p);
    }

    public BigDecimal getQuantityAsDecimal() {
        return new BigDecimal(q);
    }

    public boolean isBuyerMarketMaker() {
        return Boolean.TRUE.equals(m);
    }
}