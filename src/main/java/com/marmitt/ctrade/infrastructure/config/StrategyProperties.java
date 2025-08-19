package com.marmitt.ctrade.infrastructure.config;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.util.Map;
import java.util.HashMap;

@Setter
@Getter
@NoArgsConstructor
@Configuration
@ConfigurationProperties(prefix = "trading.strategies")
public class StrategyProperties {
    
    private Map<String, StrategyConfig> strategies = new HashMap<>();
    private boolean autoRegister = true;
    private int maxConcurrentStrategies = 10;

    @Setter
    @Getter
    @NoArgsConstructor
    public static class StrategyConfig {
        private boolean enabled = false;
        private Map<String, Object> parameters = new HashMap<>();
        private int priority = 1;
        private BigDecimal maxOrderValue = BigDecimal.valueOf(1000);
        private BigDecimal minOrderValue = BigDecimal.valueOf(10);
    }
    
    public StrategyConfig getStrategyConfig(String strategyName) {
        return strategies.getOrDefault(strategyName, new StrategyConfig());
    }
    
    public void addStrategyConfig(String strategyName, StrategyConfig config) {
        strategies.put(strategyName, config);
    }
}