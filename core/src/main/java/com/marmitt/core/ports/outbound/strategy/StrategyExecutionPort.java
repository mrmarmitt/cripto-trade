package com.marmitt.core.ports.outbound.strategy;

import com.marmitt.core.domain.StrategyInput;
import com.marmitt.core.domain.StrategyOutput;

public interface StrategyExecutionPort {
    
    StrategyOutput executeStrategy(String strategyName, StrategyInput input);
    
    boolean isStrategyAvailable(String strategyName);
    
    String getStrategyVersion(String strategyName);
    
    boolean isStrategyEnabled(String strategyName);
    
    void enableStrategy(String strategyName);
    
    void disableStrategy(String strategyName);
}