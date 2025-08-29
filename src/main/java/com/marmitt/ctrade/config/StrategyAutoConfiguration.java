package com.marmitt.ctrade.config;

import com.marmitt.ctrade.application.strategy.PairTradingStrategy;
import com.marmitt.ctrade.domain.port.TradingStrategy;
import com.marmitt.ctrade.infrastructure.config.StrategyProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class StrategyAutoConfiguration {
    
    private final StrategyRegistry strategyRegistry;
    private final StrategyProperties strategyProperties;
    private final ApplicationContext applicationContext;
    
    @PostConstruct
    public void autoRegisterStrategies() {
        if (!strategyProperties.isAutoRegister()) {
            log.info("Strategy auto-registration is disabled");
            return;
        }
        
        log.info("Starting automatic strategy registration...");
        
        registerStrategyBeansFromContext();
        registerConfiguredStrategies();
        
        log.info("Automatic strategy registration completed. Registered: {}, Active: {}", 
                strategyRegistry.getRegisteredCount(), strategyRegistry.getActiveCount());
    }
    
    private void registerStrategyBeansFromContext() {
        Map<String, TradingStrategy> strategyBeans = applicationContext.getBeansOfType(TradingStrategy.class);
        
        for (TradingStrategy strategy : strategyBeans.values()) {
            try {
                strategyRegistry.registerStrategy(strategy);
                log.debug("Auto-registered strategy bean: {}", strategy.getStrategyName());
            } catch (Exception e) {
                log.error("Failed to register strategy bean {}: {}", 
                        strategy.getStrategyName(), e.getMessage());
            }
        }
    }
    
    private void registerConfiguredStrategies() {
        Map<String, StrategyProperties.StrategyConfig> configuredStrategies = 
                strategyProperties.getStrategies();
        
        for (Map.Entry<String, StrategyProperties.StrategyConfig> entry : configuredStrategies.entrySet()) {
            String strategyName = entry.getKey();
            StrategyProperties.StrategyConfig config = entry.getValue();
            
            if (strategyRegistry.getStrategy(strategyName).isPresent()) {
                log.debug("Strategy {} already registered, updating configuration", strategyName);
                continue;
            }
            
            try {
                TradingStrategy strategy = createStrategyInstance(strategyName, config);
                if (strategy != null) {
                    strategyRegistry.registerStrategy(strategy);
                    log.info("Auto-registered configured strategy: {} (enabled: {})", 
                            strategyName, config.isEnabled());
                }
            } catch (Exception e) {
                log.error("Failed to create and register strategy {}: {}", 
                        strategyName, e.getMessage());
            }
        }
    }
    
    private TradingStrategy createStrategyInstance(String strategyName, StrategyProperties.StrategyConfig config) {
        switch (strategyName.toLowerCase()) {
            case "pair-trading-strategy":
                return new PairTradingStrategy(config);
            
            default:
                log.warn("Unknown strategy type in configuration: {}", strategyName);
                return null;
        }
    }
    
    public void registerStrategy(TradingStrategy strategy) {
        strategyRegistry.registerStrategy(strategy);
    }
    
    public void createAndRegisterStrategy(String strategyType, Map<String, Object> parameters) {
        StrategyProperties.StrategyConfig config = new StrategyProperties.StrategyConfig();
        config.setParameters(parameters);
        config.setEnabled(true);
        
        TradingStrategy strategy = createStrategyInstance(strategyType, config);
        if (strategy != null) {
            strategyRegistry.registerStrategy(strategy);
            log.info("Dynamically created and registered strategy: {}", strategyType);
        } else {
            log.error("Failed to create strategy of type: {}", strategyType);
        }
    }
}