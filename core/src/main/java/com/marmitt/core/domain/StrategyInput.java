package com.marmitt.core.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

public record StrategyInput(
        Symbol symbol,
        BigDecimal currentPrice,
        BigDecimal previousPrice,
        BigDecimal bidPrice,
        BigDecimal askPrice,
        BigDecimal volume,
        BigDecimal high24h,
        BigDecimal low24h,
        Instant timestamp,
        Map<String, Object> additionalData
) {
    public static Builder builder() {
        return new Builder();
    }
    
    public BigDecimal getPriceChange() {
        if (previousPrice == null || currentPrice == null) {
            return BigDecimal.ZERO;
        }
        return currentPrice.subtract(previousPrice);
    }
    
    public BigDecimal getPriceChangePercent() {
        if (previousPrice == null || previousPrice.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return getPriceChange().divide(previousPrice, 4, java.math.RoundingMode.HALF_UP)
               .multiply(BigDecimal.valueOf(100));
    }
    
    public BigDecimal getSpread() {
        if (bidPrice == null || askPrice == null) {
            return BigDecimal.ZERO;
        }
        return askPrice.subtract(bidPrice);
    }

    public static class Builder {
        private Symbol symbol;
        private BigDecimal currentPrice;
        private BigDecimal previousPrice;
        private BigDecimal bidPrice;
        private BigDecimal askPrice;
        private BigDecimal volume;
        private BigDecimal high24h;
        private BigDecimal low24h;
        private Instant timestamp = Instant.now();
        private Map<String, Object> additionalData = Map.of();

        public Builder symbol(Symbol symbol) {
            this.symbol = symbol;
            return this;
        }

        public Builder currentPrice(BigDecimal currentPrice) {
            this.currentPrice = currentPrice;
            return this;
        }

        public Builder previousPrice(BigDecimal previousPrice) {
            this.previousPrice = previousPrice;
            return this;
        }

        public Builder bidPrice(BigDecimal bidPrice) {
            this.bidPrice = bidPrice;
            return this;
        }

        public Builder askPrice(BigDecimal askPrice) {
            this.askPrice = askPrice;
            return this;
        }

        public Builder volume(BigDecimal volume) {
            this.volume = volume;
            return this;
        }

        public Builder high24h(BigDecimal high24h) {
            this.high24h = high24h;
            return this;
        }

        public Builder low24h(BigDecimal low24h) {
            this.low24h = low24h;
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder additionalData(Map<String, Object> additionalData) {
            this.additionalData = additionalData;
            return this;
        }

        public StrategyInput build() {
            return new StrategyInput(symbol, currentPrice, previousPrice, bidPrice, askPrice,
                                   volume, high24h, low24h, timestamp, additionalData);
        }
    }
}