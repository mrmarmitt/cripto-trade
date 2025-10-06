package com.marmitt.core.dto.trade;

import com.marmitt.core.domain.Symbol;
import com.marmitt.core.enums.TradingAction;
import com.marmitt.core.enums.TradingStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record TradingResult(
        String requestId,
        String orderId,
        Symbol symbol,
        TradingAction action,
        BigDecimal quantity,
        BigDecimal executedPrice,
        TradingStatus status,
        String message,
        Instant timestamp
) {
    public static TradingResult success(String requestId, String orderId, Symbol symbol, 
                                      TradingAction action, BigDecimal quantity, BigDecimal executedPrice) {
        return new TradingResult(requestId, orderId, symbol, action, quantity, 
                               executedPrice, TradingStatus.SUCCESS, "Trade executed successfully", 
                               Instant.now());
    }
    
    public static TradingResult failed(String requestId, Symbol symbol, TradingAction action, 
                                     String errorMessage) {
        return new TradingResult(requestId, null, symbol, action, null, null, 
                               TradingStatus.FAILED, errorMessage, Instant.now());
    }
    
    public static TradingResult pending(String requestId, String orderId, Symbol symbol, 
                                      TradingAction action, BigDecimal quantity) {
        return new TradingResult(requestId, orderId, symbol, action, quantity, null, 
                               TradingStatus.PENDING, "Trade pending execution", Instant.now());
    }
    
    public boolean isSuccess() {
        return TradingStatus.SUCCESS.equals(status);
    }
    
    public boolean isFailed() {
        return TradingStatus.FAILED.equals(status);
    }
    
    public boolean isPending() {
        return TradingStatus.PENDING.equals(status);
    }
}