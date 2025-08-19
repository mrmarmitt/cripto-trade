package com.marmitt.ctrade.application.strategy;

import com.marmitt.ctrade.domain.port.TradingStrategy;
import com.marmitt.ctrade.infrastructure.config.StrategyProperties;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;
import java.util.HashMap;

public abstract class AbstractTradingStrategy implements TradingStrategy {
    
    @Getter
    @Setter
    private boolean enabled = false;
    
    @Getter
    private final String strategyName;
    
    private final Map<String, Object> configuration;
    
    protected AbstractTradingStrategy(String strategyName) {
        this.strategyName = strategyName;
        this.configuration = new HashMap<>();
    }
    
    protected AbstractTradingStrategy(String strategyName, StrategyProperties.StrategyConfig config) {
        this.strategyName = strategyName;
        this.enabled = config.isEnabled();
        this.configuration = new HashMap<>(config.getParameters());
    }
    
    @Override
    public Map<String, Object> getConfiguration() {
        return new HashMap<>(configuration);
    }
    
    protected void setConfigurationParameter(String key, Object value) {
        configuration.put(key, value);
    }
    
    protected <T> T getConfigurationParameter(String key, Class<T> type, T defaultValue) {
        Object value = configuration.get(key);
        if (value != null && type.isInstance(value)) {
            return type.cast(value);
        }
        return defaultValue;
    }
    
    protected String getStringParameter(String key, String defaultValue) {
        return getConfigurationParameter(key, String.class, defaultValue);
    }
    
    protected Double getDoubleParameter(String key, Double defaultValue) {
        return getConfigurationParameter(key, Double.class, defaultValue);
    }
    
    protected Integer getIntegerParameter(String key, Integer defaultValue) {
        return getConfigurationParameter(key, Integer.class, defaultValue);
    }
    
    protected Boolean getBooleanParameter(String key, Boolean defaultValue) {
        return getConfigurationParameter(key, Boolean.class, defaultValue);
    }
}