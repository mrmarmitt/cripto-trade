package com.marmitt.core.domain.data;

import com.marmitt.core.domain.Symbol;

import java.math.BigDecimal;
import java.time.Instant;

public record MarketData(
        Symbol symbol,
        BigDecimal price,
        BigDecimal bidPrice,
        BigDecimal askPrice,
        BigDecimal volume,
        BigDecimal high24h,
        BigDecimal low24h,
        BigDecimal priceChange24h,
        BigDecimal priceChangePercent24h,
        Instant timestamp
) implements ProcessorResponse {

    public BigDecimal getSpread() {
        if (bidPrice == null || askPrice == null) {
            return BigDecimal.ZERO;
        }
        return askPrice.subtract(bidPrice);
    }
    
    public BigDecimal getMidPrice() {
        if (bidPrice == null || askPrice == null) {
            return price;
        }
        return bidPrice.add(askPrice).divide(BigDecimal.valueOf(2));
    }
    
    public boolean hasValidPrices() {
        return price != null && price.compareTo(BigDecimal.ZERO) > 0;
    }
}