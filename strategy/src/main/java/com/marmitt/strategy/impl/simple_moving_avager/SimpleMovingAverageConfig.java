package com.marmitt.strategy.impl.simple_moving_avager;

import java.math.BigDecimal;

public record SimpleMovingAverageConfig(
        int movingAveragePeriod,
        BigDecimal buyThreshold,
        BigDecimal sellThreshold,
        BigDecimal allocationPercentage     // % do capital/posição a ser usado (0.0 - 1.0)
) {
    
    public SimpleMovingAverageConfig {
        if (allocationPercentage == null || 
            allocationPercentage.compareTo(BigDecimal.ZERO) <= 0 || 
            allocationPercentage.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("Allocation percentage must be between 0.0 and 1.0");
        }
    }
    
    /**
     * Configuração padrão para TESTES - ultra sensível
     * Qualquer variação mínima de preço dispara decisão
     *
     * Para BTC a $95,000:
     * - Threshold de 0.000001 (0.0001%) = ~$0.10 de variação
     */
    public static SimpleMovingAverageConfig defaultConfig() {
        return new SimpleMovingAverageConfig(
                3,                                      // 3 períodos (mais responsivo possível)
                BigDecimal.valueOf(-0.000001),          // -0.0001% para comprar (~$0.10 para BTC)
                BigDecimal.valueOf(0.000001),           // +0.0001% para vender (~$0.10 para BTC)
                BigDecimal.valueOf(0.1)                 // 10% do capital por operação
        );
    }

    /**
     * Configuração para produção com thresholds mais conservadores
     */
    public static SimpleMovingAverageConfig productionConfig() {
        return new SimpleMovingAverageConfig(
                20,                                     // 20 períodos para média móvel
                BigDecimal.valueOf(-0.02),              // -2% para comprar
                BigDecimal.valueOf(0.02),               // +2% para vender
                BigDecimal.valueOf(0.1)                 // 10% do capital por operação
        );
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private int movingAveragePeriod = 10;
        private BigDecimal buyThreshold = BigDecimal.valueOf(-0.02);
        private BigDecimal sellThreshold = BigDecimal.valueOf(0.02);
        private BigDecimal allocationPercentage = BigDecimal.valueOf(0.1);
        
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
        
        public Builder allocationPercentage(double allocationPercentage) {
            this.allocationPercentage = BigDecimal.valueOf(allocationPercentage);
            return this;
        }
        
        public SimpleMovingAverageConfig build() {
            return new SimpleMovingAverageConfig(movingAveragePeriod, buyThreshold, 
                                               sellThreshold, allocationPercentage);
        }
    }
}