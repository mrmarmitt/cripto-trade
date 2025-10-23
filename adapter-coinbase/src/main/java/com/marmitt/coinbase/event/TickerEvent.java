package com.marmitt.coinbase.event;

import java.math.BigDecimal;

public record TickerEvent(
        String type,                    // Event type (ticker)
        String sequence,                // Sequence number  
        String product_id,              // Symbol (e.g., BTC-USD)
        String price,                   // Last price
        String open_24h,               // 24h opening price
        String volume_24h,             // 24h volume
        String low_24h,                // 24h low price
        String high_24h,               // 24h high price
        String volume_30d,             // 30d volume
        String best_bid,               // Best bid price
        String best_bid_size,          // Best bid size
        String best_ask,               // Best ask price
        String best_ask_size,          // Best ask size
        String side,                   // Last trade side
        String time,                   // Timestamp
        Long trade_id,                 // Trade ID
        String last_size               // Size of last trade
) {
    public BigDecimal getLastPriceAsDecimal() {
        return new BigDecimal(price);
    }

    public BigDecimal getBestBidPriceAsDecimal() {
        return best_bid != null ? new BigDecimal(best_bid) : null;
    }

    public BigDecimal getBestAskPriceAsDecimal() {
        return best_ask != null ? new BigDecimal(best_ask) : null;
    }

    public BigDecimal getBestBidSizeAsDecimal() {
        return best_bid_size != null ? new BigDecimal(best_bid_size) : null;
    }

    public BigDecimal getBestAskSizeAsDecimal() {
        return best_ask_size != null ? new BigDecimal(best_ask_size) : null;
    }
}