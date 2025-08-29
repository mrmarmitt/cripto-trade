package com.marmitt.ctrade.config;

import com.marmitt.ctrade.domain.port.TradingStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
public class StrategyRegistry {
    
    private final Map<String, TradingStrategy> strategies = new ConcurrentHashMap<>();
    
    public void registerStrategy(TradingStrategy strategy) {
        if (strategy == null) {
            throw new IllegalArgumentException("Strategy cannot be null");
        }
        
        String strategyName = strategy.getStrategyName();
        if (strategyName == null || strategyName.trim().isEmpty()) {
            throw new IllegalArgumentException("Strategy name cannot be null or empty");
        }
        
        strategies.put(strategyName, strategy);
        log.info("Registered strategy: {}", strategyName);
    }
    
    public void unregisterStrategy(String strategyName) {
        TradingStrategy removed = strategies.remove(strategyName);
        if (removed != null) {
            log.info("Unregistered strategy: {}", strategyName);
        }
    }
    
    public Optional<TradingStrategy> getStrategy(String name) {
        return Optional.ofNullable(strategies.get(name));
    }
    
    public List<TradingStrategy> getAllStrategies() {
        return new ArrayList<>(strategies.values());
    }
    
    public List<TradingStrategy> getActiveStrategies() {
        return strategies.values().stream()
                .filter(TradingStrategy::isEnabled)
                .collect(Collectors.toList());
    }
    
    public Set<String> getRegisteredStrategyNames() {
        return new HashSet<>(strategies.keySet());
    }
    
    public int getRegisteredCount() {
        return strategies.size();
    }
    
    public int getActiveCount() {
        return (int) strategies.values().stream()
                .filter(TradingStrategy::isEnabled)
                .count();
    }
    
    public void enableStrategy(String strategyName) {
        TradingStrategy strategy = strategies.get(strategyName);
        if (strategy != null) {
            strategy.setEnabled(true);
            log.info("Enabled strategy: {}", strategyName);
        } else {
            log.warn("Strategy not found for enabling: {}", strategyName);
        }
    }
    
    public void disableStrategy(String strategyName) {
        TradingStrategy strategy = strategies.get(strategyName);
        if (strategy != null) {
            strategy.setEnabled(false);
            log.info("Disabled strategy: {}", strategyName);
        } else {
            log.warn("Strategy not found for disabling: {}", strategyName);
        }
    }
}