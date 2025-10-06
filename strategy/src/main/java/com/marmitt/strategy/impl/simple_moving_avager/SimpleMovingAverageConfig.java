package com.marmitt.strategy.impl.simple_moving_avager;

import java.math.BigDecimal;

public record SimpleMovingAverageConfig(
        int movingAveragePeriod,
        BigDecimal buyThreshold,
        BigDecimal sellThreshold,
        BigDecimal tradingQuantity
) {
    public static SimpleMovingAverageConfig defaultConfig() {
        return new SimpleMovingAverageConfig(
                10,                                    // 10 períodos para média móvel
                BigDecimal.valueOf(-0.02),             // -2% para comprar
                BigDecimal.valueOf(0.02),              // +2% para vender
                BigDecimal.valueOf(100.0)              // Quantidade padrão
        );
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private int movingAveragePeriod = 10;
        private BigDecimal buyThreshold = BigDecimal.valueOf(-0.02);
        private BigDecimal sellThreshold = BigDecimal.valueOf(0.02);
        private BigDecimal tradingQuantity = BigDecimal.valueOf(100.0);
        
        public Builder movingAveragePeriod(int movingAveragePeriod) {
            this.movingAveragePeriod = movingAveragePeriod;
            return this;
        }
        
        public Builder buyThreshold(double buyThreshold) {
            this.buyThreshold = BigDecimal.valueOf(buyThreshold);
            return this;
        }
        
        public Builder sellThreshold(double sellThreshold) {
            this.sellThreshold = BigDecimal.valueOf(sellThreshold);
            return this;
        }
        
        public Builder tradingQuantity(double tradingQuantity) {
            this.tradingQuantity = BigDecimal.valueOf(tradingQuantity);
            return this;
        }
        
        public SimpleMovingAverageConfig build() {
            return new SimpleMovingAverageConfig(movingAveragePeriod, buyThreshold, 
                                               sellThreshold, tradingQuantity);
        }
    }
}