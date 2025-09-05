package com.marmitt.core.ports.inbound;

import com.marmitt.core.dto.strategy.StrategyConfiguration;
import com.marmitt.core.dto.strategy.StrategyExecutionResult;
import com.marmitt.core.dto.strategy.StrategyInfo;

import java.util.List;

public interface StrategyManagementPort {
    
    List<StrategyInfo> getAvailableStrategies();
    
    StrategyInfo getStrategyInfo(String strategyName);
    
    void registerStrategy(String strategyName, String strategyClass);
    
    void unregisterStrategy(String strategyName);
    
    StrategyExecutionResult executeStrategy(String strategyName, StrategyConfiguration config);
    
    void enableStrategy(String strategyName);
    
    void disableStrategy(String strategyName);
    
    boolean isStrategyEnabled(String strategyName);
}