package com.marmitt.core.dto.websocket.data;

import com.marmitt.core.domain.Symbol;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public record MarketDataDto(
        String exchangeName,
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

    /**
     * {@code timestamp} e o instante de recebimento do tick e e obrigatorio: a mailbox do runner o
     * usa para ordem e idade (T42).
     */
    public MarketDataDto {
        Objects.requireNonNull(timestamp, "timestamp cannot be null");
    }

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