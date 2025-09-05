package com.marmitt.core.domain;

import com.marmitt.core.enums.TradingAction;
import java.math.BigDecimal;

public record TradingDecision(
        Symbol symbol,
        TradingAction recommendedAction,
        BigDecimal suggestedQuantity,
        BigDecimal suggestedPrice,
        BigDecimal confidence,
        String reasoning,
        String strategyName
) {
    public static TradingDecision buy(Symbol symbol, BigDecimal quantity, BigDecimal price, 
                                    BigDecimal confidence, String reasoning, String strategyName) {
        return new TradingDecision(symbol, TradingAction.BUY, quantity, price, 
                                 confidence, reasoning, strategyName);
    }
    
    public static TradingDecision sell(Symbol symbol, BigDecimal quantity, BigDecimal price, 
                                     BigDecimal confidence, String reasoning, String strategyName) {
        return new TradingDecision(symbol, TradingAction.SELL, quantity, price, 
                                 confidence, reasoning, strategyName);
    }
    
    public static TradingDecision hold(Symbol symbol, String reasoning, String strategyName) {
        return new TradingDecision(symbol, TradingAction.HOLD, null, null, 
                                 BigDecimal.ZERO, reasoning, strategyName);
    }
    
    public boolean shouldTrade() {
        return TradingAction.BUY.equals(recommendedAction) || TradingAction.SELL.equals(recommendedAction);
    }
    
    public boolean shouldHold() {
        return TradingAction.HOLD.equals(recommendedAction);
    }
}