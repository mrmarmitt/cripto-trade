package com.marmitt.core.dto.trade;

import com.marmitt.core.domain.Symbol;
import com.marmitt.core.enums.TradingAction;

import java.math.BigDecimal;
import java.time.Instant;

public record TradingRequest(
        String requestId,
        Symbol symbol,
        TradingAction action,
        BigDecimal quantity,
        BigDecimal price,
        String strategyName,
        Instant timestamp
) {
    public static TradingRequest buy(String requestId, Symbol symbol, BigDecimal quantity, 
                                   BigDecimal price, String strategyName) {
        return new TradingRequest(requestId, symbol, TradingAction.BUY, quantity, 
                                price, strategyName, Instant.now());
    }
    
    public static TradingRequest sell(String requestId, Symbol symbol, BigDecimal quantity, 
                                    BigDecimal price, String strategyName) {
        return new TradingRequest(requestId, symbol, TradingAction.SELL, quantity, 
                                price, strategyName, Instant.now());
    }
    
    public boolean isBuy() {
        return TradingAction.BUY.equals(action);
    }
    
    public boolean isSell() {
        return TradingAction.SELL.equals(action);
    }
}