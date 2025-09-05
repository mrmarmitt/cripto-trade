package com.marmitt.core.ports.inbound;

import com.marmitt.core.domain.TradingDecision;
import com.marmitt.core.dto.trade.TradingRequest;
import com.marmitt.core.dto.trade.TradingResult;

import java.util.concurrent.CompletableFuture;

public interface TradingOrchestrationPort {
    
    CompletableFuture<TradingResult> executeTrade(TradingRequest request);
    
    TradingDecision analyzeMarket(String symbol);
    
    void startTrading(String strategyName, String symbol);
    
    void stopTrading(String symbol);
    
    boolean isTradingActive(String symbol);
}