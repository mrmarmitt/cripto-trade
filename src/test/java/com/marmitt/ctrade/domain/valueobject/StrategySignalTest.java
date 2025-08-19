package com.marmitt.ctrade.domain.valueobject;

import com.marmitt.ctrade.domain.entity.TradingPair;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class StrategySignalTest {

    @Test
    void shouldCreateHoldSignal() {
        StrategySignal signal = StrategySignal.hold("TestStrategy");
        
        assertEquals(SignalType.HOLD, signal.getType());
        assertEquals("TestStrategy", signal.getStrategyName());
        assertEquals("No action required", signal.getReason());
        assertNull(signal.getPair());
        assertNull(signal.getQuantity());
        assertNull(signal.getPrice());
        assertFalse(signal.isActionable());
    }
    
    @Test
    void shouldCreateBuySignal() {
        TradingPair pair = new TradingPair("BTC/USDT");
        BigDecimal quantity = new BigDecimal("0.001");
        BigDecimal price = new BigDecimal("50000");
        String reason = "Test buy signal";
        String strategyName = "TestStrategy";
        
        StrategySignal signal = StrategySignal.buy(pair, quantity, price, reason, strategyName);
        
        assertEquals(SignalType.BUY, signal.getType());
        assertEquals(pair, signal.getPair());
        assertEquals(quantity, signal.getQuantity());
        assertEquals(price, signal.getPrice());
        assertEquals(reason, signal.getReason());
        assertEquals(strategyName, signal.getStrategyName());
        assertTrue(signal.isActionable());
    }
    
    @Test
    void shouldCreateSellSignal() {
        TradingPair pair = new TradingPair("ETH/USDT");
        BigDecimal quantity = new BigDecimal("0.1");
        BigDecimal price = new BigDecimal("3000");
        String reason = "Test sell signal";
        String strategyName = "TestStrategy";
        
        StrategySignal signal = StrategySignal.sell(pair, quantity, price, reason, strategyName);
        
        assertEquals(SignalType.SELL, signal.getType());
        assertEquals(pair, signal.getPair());
        assertEquals(quantity, signal.getQuantity());
        assertEquals(price, signal.getPrice());
        assertEquals(reason, signal.getReason());
        assertEquals(strategyName, signal.getStrategyName());
        assertTrue(signal.isActionable());
    }
    
    @Test
    void shouldDetermineActionableSignals() {
        StrategySignal holdSignal = StrategySignal.hold("TestStrategy");
        StrategySignal buySignal = StrategySignal.buy(
                new TradingPair("BTC/USDT"), 
                BigDecimal.ONE, 
                BigDecimal.valueOf(50000), 
                "Test", 
                "TestStrategy"
        );
        StrategySignal sellSignal = StrategySignal.sell(
                new TradingPair("BTC/USDT"), 
                BigDecimal.ONE, 
                BigDecimal.valueOf(50000), 
                "Test", 
                "TestStrategy"
        );
        
        assertFalse(holdSignal.isActionable());
        assertTrue(buySignal.isActionable());
        assertTrue(sellSignal.isActionable());
    }
}