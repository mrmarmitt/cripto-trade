package com.marmitt.ctrade.domain.port;

import com.marmitt.ctrade.domain.entity.MarketData;
import com.marmitt.ctrade.domain.entity.Portfolio;
import com.marmitt.ctrade.domain.valueobject.StrategySignal;

import java.util.Map;

public interface TradingStrategy {
    StrategySignal analyze(MarketData marketData, Portfolio portfolio);
    
    String getStrategyName();
    
    Map<String, Object> getConfiguration();
    
    boolean isEnabled();
    
    void setEnabled(boolean enabled);
}