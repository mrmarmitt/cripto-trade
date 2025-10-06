package com.marmitt.core.dto.strategy;

import java.util.Map;

public record StrategyConfiguration(
        String strategyName,
        Map<String, Object> parameters,
        boolean enabled
) {
    public static StrategyConfiguration of(String strategyName, Map<String, Object> parameters) {
        return new StrategyConfiguration(strategyName, parameters, true);
    }
    
    public static StrategyConfiguration disabled(String strategyName, Map<String, Object> parameters) {
        return new StrategyConfiguration(strategyName, parameters, false);
    }
    
    public Object getParameter(String key) {
        return parameters.get(key);
    }
    
    public String getStringParameter(String key) {
        Object value = parameters.get(key);
        return value != null ? value.toString() : null;
    }
    
    public String getStringParameter(String key, String defaultValue) {
        String value = getStringParameter(key);
        return value != null ? value : defaultValue;
    }
    
    public Integer getIntParameter(String key) {
        Object value = parameters.get(key);
        if (value == null) return null;
        if (value instanceof Integer) return (Integer) value;
        try {
            return Integer.valueOf(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
    
    public Integer getIntParameter(String key, Integer defaultValue) {
        Integer value = getIntParameter(key);
        return value != null ? value : defaultValue;
    }
    
    public Double getDoubleParameter(String key) {
        Object value = parameters.get(key);
        if (value == null) return null;
        if (value instanceof Double) return (Double) value;
        if (value instanceof Number) return ((Number) value).doubleValue();
        try {
            return Double.valueOf(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
    
    public Double getDoubleParameter(String key, Double defaultValue) {
        Double value = getDoubleParameter(key);
        return value != null ? value : defaultValue;
    }
    
    public Boolean getBooleanParameter(String key) {
        Object value = parameters.get(key);
        if (value == null) return null;
        if (value instanceof Boolean) return (Boolean) value;
        return Boolean.valueOf(value.toString());
    }
    
    public Boolean getBooleanParameter(String key, Boolean defaultValue) {
        Boolean value = getBooleanParameter(key);
        return value != null ? value : defaultValue;
    }
    
    public boolean hasParameter(String key) {
        return parameters.containsKey(key);
    }
}