package com.marmitt.core.ports.outbound.strategy;

import com.marmitt.core.domain.StrategyInput;
import com.marmitt.core.domain.StrategyOutput;

import java.util.UUID;

public interface TradingStrategy {
    
    UUID getStrategyId();
    
    StrategyOutput executeStrategy(StrategyInput inputData);

    String getStrategyName();

    String getStrategyVersion();

    boolean isEnabled();

    void setEnabled(boolean enabled);
}