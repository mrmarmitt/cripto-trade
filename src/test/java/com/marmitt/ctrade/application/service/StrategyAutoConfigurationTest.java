package com.marmitt.ctrade.application.service;

import com.marmitt.ctrade.application.strategy.PairTradingStrategy;
import com.marmitt.ctrade.domain.port.TradingStrategy;
import com.marmitt.ctrade.infrastructure.config.StrategyProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationContext;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StrategyAutoConfigurationTest {

    @Mock
    private StrategyRegistry strategyRegistry;

    @Mock
    private StrategyProperties strategyProperties;

    @Mock
    private ApplicationContext applicationContext;

    @Mock
    private TradingStrategy mockStrategy;

    @InjectMocks
    private StrategyAutoConfiguration strategyAutoConfiguration;

    @BeforeEach
    void setUp() {
        lenient().when(mockStrategy.getStrategyName()).thenReturn("TestStrategy");
    }

    @Test
    void shouldNotAutoRegisterWhenDisabled() {
        when(strategyProperties.isAutoRegister()).thenReturn(false);

        strategyAutoConfiguration.autoRegisterStrategies();

        verify(strategyProperties).isAutoRegister();
        verify(applicationContext, never()).getBeansOfType(TradingStrategy.class);
        verify(strategyRegistry, never()).registerStrategy(any());
    }

    @Test
    void shouldAutoRegisterWhenEnabled() {
        when(strategyProperties.isAutoRegister()).thenReturn(true);
        Map<String, TradingStrategy> strategyBeans = new HashMap<>();
        strategyBeans.put("testStrategy", mockStrategy);
        when(applicationContext.getBeansOfType(TradingStrategy.class)).thenReturn(strategyBeans);
        when(strategyProperties.getStrategies()).thenReturn(new HashMap<>());
        when(strategyRegistry.getRegisteredCount()).thenReturn(1);
        when(strategyRegistry.getActiveCount()).thenReturn(1);

        strategyAutoConfiguration.autoRegisterStrategies();

        verify(strategyProperties).isAutoRegister();
        verify(applicationContext).getBeansOfType(TradingStrategy.class);
        verify(strategyRegistry).registerStrategy(mockStrategy);
        verify(strategyRegistry).getRegisteredCount();
        verify(strategyRegistry).getActiveCount();
    }

    @Test
    void shouldHandleExceptionWhenRegisteringStrategyBean() {
        when(strategyProperties.isAutoRegister()).thenReturn(true);
        Map<String, TradingStrategy> strategyBeans = new HashMap<>();
        strategyBeans.put("testStrategy", mockStrategy);
        when(applicationContext.getBeansOfType(TradingStrategy.class)).thenReturn(strategyBeans);
        when(strategyProperties.getStrategies()).thenReturn(new HashMap<>());
        when(strategyRegistry.getRegisteredCount()).thenReturn(0);
        when(strategyRegistry.getActiveCount()).thenReturn(0);
        doThrow(new RuntimeException("Registration failed")).when(strategyRegistry).registerStrategy(mockStrategy);

        strategyAutoConfiguration.autoRegisterStrategies();

        verify(strategyRegistry).registerStrategy(mockStrategy);
    }

    @Test
    void shouldRegisterConfiguredPairTradingStrategy() {
        when(strategyProperties.isAutoRegister()).thenReturn(true);
        when(applicationContext.getBeansOfType(TradingStrategy.class)).thenReturn(new HashMap<>());
        
        Map<String, StrategyProperties.StrategyConfig> configuredStrategies = new HashMap<>();
        StrategyProperties.StrategyConfig config = new StrategyProperties.StrategyConfig();
        config.setEnabled(true);
        configuredStrategies.put("pairtradingstrategy", config);
        
        when(strategyProperties.getStrategies()).thenReturn(configuredStrategies);
        when(strategyRegistry.getStrategy("pairtradingstrategy")).thenReturn(Optional.empty());
        when(strategyRegistry.getRegisteredCount()).thenReturn(1);
        when(strategyRegistry.getActiveCount()).thenReturn(1);

        strategyAutoConfiguration.autoRegisterStrategies();

        verify(strategyRegistry).registerStrategy(any(PairTradingStrategy.class));
    }

    @Test
    void shouldRegisterConfiguredPairTradingStrategyWithAlternateName() {
        when(strategyProperties.isAutoRegister()).thenReturn(true);
        when(applicationContext.getBeansOfType(TradingStrategy.class)).thenReturn(new HashMap<>());
        
        Map<String, StrategyProperties.StrategyConfig> configuredStrategies = new HashMap<>();
        StrategyProperties.StrategyConfig config = new StrategyProperties.StrategyConfig();
        config.setEnabled(true);
        configuredStrategies.put("pair-trading", config);
        
        when(strategyProperties.getStrategies()).thenReturn(configuredStrategies);
        when(strategyRegistry.getStrategy("pair-trading")).thenReturn(Optional.empty());
        when(strategyRegistry.getRegisteredCount()).thenReturn(1);
        when(strategyRegistry.getActiveCount()).thenReturn(1);

        strategyAutoConfiguration.autoRegisterStrategies();

        verify(strategyRegistry).registerStrategy(any(PairTradingStrategy.class));
    }

    @Test
    void shouldSkipAlreadyRegisteredStrategy() {
        when(strategyProperties.isAutoRegister()).thenReturn(true);
        when(applicationContext.getBeansOfType(TradingStrategy.class)).thenReturn(new HashMap<>());
        
        Map<String, StrategyProperties.StrategyConfig> configuredStrategies = new HashMap<>();
        StrategyProperties.StrategyConfig config = new StrategyProperties.StrategyConfig();
        config.setEnabled(true);
        configuredStrategies.put("pairtradingstrategy", config);
        
        when(strategyProperties.getStrategies()).thenReturn(configuredStrategies);
        when(strategyRegistry.getStrategy("pairtradingstrategy")).thenReturn(Optional.of(mockStrategy));
        when(strategyRegistry.getRegisteredCount()).thenReturn(1);
        when(strategyRegistry.getActiveCount()).thenReturn(1);

        strategyAutoConfiguration.autoRegisterStrategies();

        verify(strategyRegistry, never()).registerStrategy(any(PairTradingStrategy.class));
    }

    @Test
    void shouldHandleUnknownStrategyType() {
        when(strategyProperties.isAutoRegister()).thenReturn(true);
        when(applicationContext.getBeansOfType(TradingStrategy.class)).thenReturn(new HashMap<>());
        
        Map<String, StrategyProperties.StrategyConfig> configuredStrategies = new HashMap<>();
        StrategyProperties.StrategyConfig config = new StrategyProperties.StrategyConfig();
        config.setEnabled(true);
        configuredStrategies.put("unknownstrategy", config);
        
        when(strategyProperties.getStrategies()).thenReturn(configuredStrategies);
        when(strategyRegistry.getStrategy("unknownstrategy")).thenReturn(Optional.empty());
        when(strategyRegistry.getRegisteredCount()).thenReturn(0);
        when(strategyRegistry.getActiveCount()).thenReturn(0);

        strategyAutoConfiguration.autoRegisterStrategies();

        verify(strategyRegistry, never()).registerStrategy(any());
    }

    @Test
    void shouldHandleExceptionWhenCreatingStrategy() {
        when(strategyProperties.isAutoRegister()).thenReturn(true);
        when(applicationContext.getBeansOfType(TradingStrategy.class)).thenReturn(new HashMap<>());
        
        Map<String, StrategyProperties.StrategyConfig> configuredStrategies = new HashMap<>();
        StrategyProperties.StrategyConfig config = null; // This will cause an exception
        configuredStrategies.put("pairtradingstrategy", config);
        
        when(strategyProperties.getStrategies()).thenReturn(configuredStrategies);
        when(strategyRegistry.getStrategy("pairtradingstrategy")).thenReturn(Optional.empty());
        when(strategyRegistry.getRegisteredCount()).thenReturn(0);
        when(strategyRegistry.getActiveCount()).thenReturn(0);

        strategyAutoConfiguration.autoRegisterStrategies();

        verify(strategyRegistry, never()).registerStrategy(any());
    }

    @Test
    void shouldRegisterStrategyDirectly() {
        strategyAutoConfiguration.registerStrategy(mockStrategy);

        verify(strategyRegistry).registerStrategy(mockStrategy);
    }

    @Test
    void shouldCreateAndRegisterStrategyDynamically() {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("key", "value");

        strategyAutoConfiguration.createAndRegisterStrategy("pairtradingstrategy", parameters);

        verify(strategyRegistry).registerStrategy(any(PairTradingStrategy.class));
    }

    @Test
    void shouldHandleUnknownStrategyTypeWhenCreatingDynamically() {
        Map<String, Object> parameters = new HashMap<>();

        strategyAutoConfiguration.createAndRegisterStrategy("unknownstrategy", parameters);

        verify(strategyRegistry, never()).registerStrategy(any());
    }
}