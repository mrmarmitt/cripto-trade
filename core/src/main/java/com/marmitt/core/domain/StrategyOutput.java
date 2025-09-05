package com.marmitt.core.domain;

import com.marmitt.core.enums.TradingAction;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

public record StrategyOutput(
        String strategyName,
        Symbol symbol,
        TradingAction decision,
        BigDecimal quantity,
        BigDecimal targetPrice,
        BigDecimal stopLoss,
        BigDecimal takeProfit,
        String reasoning,
        BigDecimal confidence,
        Instant timestamp,
        Map<String, Object> metadata
) {
    public static Builder builder() {
        return new Builder();
    }
    
    public static StrategyOutput hold(String strategyName, Symbol symbol, String reasoning) {
        return new StrategyOutput(strategyName, symbol, TradingAction.HOLD, null, null, 
                                 null, null, reasoning, BigDecimal.ZERO, Instant.now(), Map.of());
    }
    
    public static StrategyOutput buy(String strategyName, Symbol symbol, BigDecimal quantity, 
                                   BigDecimal targetPrice, String reasoning) {
        return new StrategyOutput(strategyName, symbol, TradingAction.BUY, quantity, 
                                 targetPrice, null, null, reasoning, BigDecimal.ONE, Instant.now(), Map.of());
    }
    
    public static StrategyOutput sell(String strategyName, Symbol symbol, BigDecimal quantity, 
                                    BigDecimal targetPrice, String reasoning) {
        return new StrategyOutput(strategyName, symbol, TradingAction.SELL, quantity, 
                                 targetPrice, null, null, reasoning, BigDecimal.ONE, Instant.now(), Map.of());
    }
    
    public boolean shouldTrade() {
        return decision == TradingAction.BUY || decision == TradingAction.SELL;
    }
    
    public boolean shouldHold() {
        return decision == TradingAction.HOLD;
    }

    public static class Builder {
        private String strategyName;
        private Symbol symbol;
        private TradingAction decision;
        private BigDecimal quantity;
        private BigDecimal targetPrice;
        private BigDecimal stopLoss;
        private BigDecimal takeProfit;
        private String reasoning;
        private BigDecimal confidence = BigDecimal.ZERO;
        private Instant timestamp = Instant.now();
        private Map<String, Object> metadata = Map.of();

        public Builder strategyName(String strategyName) {
            this.strategyName = strategyName;
            return this;
        }

        public Builder symbol(Symbol symbol) {
            this.symbol = symbol;
            return this;
        }

        public Builder decision(TradingAction decision) {
            this.decision = decision;
            return this;
        }

        public Builder quantity(BigDecimal quantity) {
            this.quantity = quantity;
            return this;
        }

        public Builder targetPrice(BigDecimal targetPrice) {
            this.targetPrice = targetPrice;
            return this;
        }

        public Builder stopLoss(BigDecimal stopLoss) {
            this.stopLoss = stopLoss;
            return this;
        }

        public Builder takeProfit(BigDecimal takeProfit) {
            this.takeProfit = takeProfit;
            return this;
        }

        public Builder reasoning(String reasoning) {
            this.reasoning = reasoning;
            return this;
        }

        public Builder confidence(BigDecimal confidence) {
            this.confidence = confidence;
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        public StrategyOutput build() {
            return new StrategyOutput(strategyName, symbol, decision, quantity, targetPrice,
                                     stopLoss, takeProfit, reasoning, confidence, timestamp, metadata);
        }
    }
}