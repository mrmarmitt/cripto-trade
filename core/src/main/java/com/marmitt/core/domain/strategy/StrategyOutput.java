package com.marmitt.core.domain.strategy;

import com.marmitt.core.enums.TradingAction;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

@Builder
public record StrategyOutput(
        String strategyName,
        TradingAction decision,
        BigDecimal confidence,    // 0.0 - 1.0
        BigDecimal quantity,      // Quantidade absoluta a ser executada (ex: 0.05 BTC)
        String reasoning,
        Instant timestamp,
        Map<String, Object> metadata
) {
    
    public static StrategyOutput hold(String strategyName, String reasoning) {
        return new StrategyOutput(strategyName, TradingAction.SHOULD_HOLD, 
                                 BigDecimal.ZERO, BigDecimal.ZERO, reasoning, 
                                 Instant.now(), Map.of());
    }
    
    public static StrategyOutput buy(String strategyName, BigDecimal confidence, 
                                   BigDecimal quantity, String reasoning) {
        validateTradeParams(confidence, quantity);
        return new StrategyOutput(strategyName, TradingAction.SHOULD_BUY, 
                                 confidence, quantity, reasoning, 
                                 Instant.now(), Map.of());
    }
    
    public static StrategyOutput sell(String strategyName, BigDecimal confidence, 
                                    BigDecimal quantity, String reasoning) {
        validateTradeParams(confidence, quantity);
        return new StrategyOutput(strategyName, TradingAction.SHOULD_SELL, 
                                 confidence, quantity, reasoning, 
                                 Instant.now(), Map.of());
    }
    
    private static void validateTradeParams(BigDecimal confidence, BigDecimal quantity) {
        if (confidence == null || confidence.compareTo(BigDecimal.ZERO) < 0 || confidence.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("Confidence must be between 0.0 and 1.0");
        }
        if (quantity != null && quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Quantity must be positive when specified");
        }
    }
    
    public boolean shouldTrade() {
        return decision == TradingAction.SHOULD_BUY || decision == TradingAction.SHOULD_SELL;
    }
    
    public boolean shouldHold() {
        return decision == TradingAction.SHOULD_HOLD;
    }
    
    public boolean isHighConfidence() {
        return confidence != null && confidence.compareTo(new BigDecimal("0.7")) >= 0;
    }

}