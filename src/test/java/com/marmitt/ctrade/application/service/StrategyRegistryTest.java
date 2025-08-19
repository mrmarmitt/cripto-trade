package com.marmitt.ctrade.application.service;

import com.marmitt.ctrade.domain.entity.MarketData;
import com.marmitt.ctrade.domain.entity.Portfolio;
import com.marmitt.ctrade.domain.port.TradingStrategy;
import com.marmitt.ctrade.domain.valueobject.StrategySignal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StrategyRegistryTest {
    
    private StrategyRegistry strategyRegistry;
    private TradingStrategy mockStrategy1;
    private TradingStrategy mockStrategy2;
    
    @BeforeEach
    void setUp() {
        strategyRegistry = new StrategyRegistry();
        
        mockStrategy1 = mock(TradingStrategy.class);
        when(mockStrategy1.getStrategyName()).thenReturn("Strategy1");
        when(mockStrategy1.isEnabled()).thenReturn(true);
        
        mockStrategy2 = mock(TradingStrategy.class);
        when(mockStrategy2.getStrategyName()).thenReturn("Strategy2");
        when(mockStrategy2.isEnabled()).thenReturn(false);
    }
    
    @Test
    void shouldRegisterStrategy() {
        strategyRegistry.registerStrategy(mockStrategy1);
        
        Optional<TradingStrategy> retrieved = strategyRegistry.getStrategy("Strategy1");
        assertTrue(retrieved.isPresent());
        assertEquals(mockStrategy1, retrieved.get());
        assertEquals(1, strategyRegistry.getRegisteredCount());
    }
    
    @Test
    void shouldThrowExceptionWhenRegisteringNullStrategy() {
        assertThrows(IllegalArgumentException.class, () -> {
            strategyRegistry.registerStrategy(null);
        });
    }
    
    @Test
    void shouldThrowExceptionWhenRegisteringStrategyWithNullName() {
        TradingStrategy nullNameStrategy = mock(TradingStrategy.class);
        when(nullNameStrategy.getStrategyName()).thenReturn(null);
        
        assertThrows(IllegalArgumentException.class, () -> {
            strategyRegistry.registerStrategy(nullNameStrategy);
        });
    }
    
    @Test
    void shouldThrowExceptionWhenRegisteringStrategyWithEmptyName() {
        TradingStrategy emptyNameStrategy = mock(TradingStrategy.class);
        when(emptyNameStrategy.getStrategyName()).thenReturn("");
        
        assertThrows(IllegalArgumentException.class, () -> {
            strategyRegistry.registerStrategy(emptyNameStrategy);
        });
    }
    
    @Test
    void shouldUnregisterStrategy() {
        strategyRegistry.registerStrategy(mockStrategy1);
        assertEquals(1, strategyRegistry.getRegisteredCount());
        
        strategyRegistry.unregisterStrategy("Strategy1");
        assertEquals(0, strategyRegistry.getRegisteredCount());
        assertFalse(strategyRegistry.getStrategy("Strategy1").isPresent());
    }
    
    @Test
    void shouldReturnEmptyWhenStrategyNotFound() {
        Optional<TradingStrategy> result = strategyRegistry.getStrategy("NonExistent");
        assertFalse(result.isPresent());
    }
    
    @Test
    void shouldReturnAllStrategies() {
        strategyRegistry.registerStrategy(mockStrategy1);
        strategyRegistry.registerStrategy(mockStrategy2);
        
        List<TradingStrategy> allStrategies = strategyRegistry.getAllStrategies();
        assertEquals(2, allStrategies.size());
        assertTrue(allStrategies.contains(mockStrategy1));
        assertTrue(allStrategies.contains(mockStrategy2));
    }
    
    @Test
    void shouldReturnOnlyActiveStrategies() {
        strategyRegistry.registerStrategy(mockStrategy1); // enabled
        strategyRegistry.registerStrategy(mockStrategy2); // disabled
        
        List<TradingStrategy> activeStrategies = strategyRegistry.getActiveStrategies();
        assertEquals(1, activeStrategies.size());
        assertTrue(activeStrategies.contains(mockStrategy1));
        assertFalse(activeStrategies.contains(mockStrategy2));
    }
    
    @Test
    void shouldReturnCorrectCounts() {
        strategyRegistry.registerStrategy(mockStrategy1); // enabled
        strategyRegistry.registerStrategy(mockStrategy2); // disabled
        
        assertEquals(2, strategyRegistry.getRegisteredCount());
        assertEquals(1, strategyRegistry.getActiveCount());
    }
    
    @Test
    void shouldEnableStrategy() {
        strategyRegistry.registerStrategy(mockStrategy2); // initially disabled
        
        strategyRegistry.enableStrategy("Strategy2");
        
        verify(mockStrategy2).setEnabled(true);
    }
    
    @Test
    void shouldDisableStrategy() {
        strategyRegistry.registerStrategy(mockStrategy1); // initially enabled
        
        strategyRegistry.disableStrategy("Strategy1");
        
        verify(mockStrategy1).setEnabled(false);
    }
    
    @Test
    void shouldHandleEnableNonExistentStrategy() {
        // Should not throw exception
        assertDoesNotThrow(() -> {
            strategyRegistry.enableStrategy("NonExistent");
        });
    }
    
    @Test
    void shouldHandleDisableNonExistentStrategy() {
        // Should not throw exception
        assertDoesNotThrow(() -> {
            strategyRegistry.disableStrategy("NonExistent");
        });
    }
    
    @Test
    void shouldReturnRegisteredStrategyNames() {
        strategyRegistry.registerStrategy(mockStrategy1);
        strategyRegistry.registerStrategy(mockStrategy2);
        
        var names = strategyRegistry.getRegisteredStrategyNames();
        assertEquals(2, names.size());
        assertTrue(names.contains("Strategy1"));
        assertTrue(names.contains("Strategy2"));
    }
}