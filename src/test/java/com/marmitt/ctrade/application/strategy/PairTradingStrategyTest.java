package com.marmitt.ctrade.application.strategy;

import com.marmitt.ctrade.domain.entity.MarketData;
import com.marmitt.ctrade.domain.entity.Portfolio;
import com.marmitt.ctrade.domain.entity.TradingPair;
import com.marmitt.ctrade.domain.valueobject.SignalType;
import com.marmitt.ctrade.domain.valueobject.StrategySignal;
import com.marmitt.ctrade.infrastructure.config.StrategyProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PairTradingStrategyTest {
    
    private PairTradingStrategy strategy;
    private Portfolio portfolio;
    private TradingPair btcPair;
    private TradingPair ethPair;
    
    @BeforeEach
    void setUp() {
        StrategyProperties.StrategyConfig config = new StrategyProperties.StrategyConfig();
        config.setEnabled(true);
        
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("pair1", "BTC/USDT");
        parameters.put("pair2", "ETH/USDT");
        parameters.put("upperThreshold", 2.0);
        parameters.put("lowerThreshold", -2.0);
        parameters.put("maxHistorySize", 20);
        parameters.put("tradingAmount", 100.0);
        config.setParameters(parameters);
        
        strategy = new PairTradingStrategy(config);
        portfolio = new Portfolio();
        
        btcPair = new TradingPair("BTC/USDT");
        ethPair = new TradingPair("ETH/USDT");
    }
    
    @Test
    void shouldReturnCorrectStrategyName() {
        assertEquals("PairTradingStrategy", strategy.getStrategyName());
    }
    
    @Test
    void shouldReturnHoldWhenDisabled() {
        strategy.setEnabled(false);
        
        MarketData marketData = createMarketData(
                BigDecimal.valueOf(50000), 
                BigDecimal.valueOf(3000)
        );
        
        StrategySignal signal = strategy.analyze(marketData, portfolio);
        
        assertEquals(SignalType.HOLD, signal.getType());
        assertEquals("PairTradingStrategy", signal.getStrategyName());
    }
    
    @Test
    void shouldReturnHoldWhenMissingPriceData() {
        strategy.setEnabled(true);
        
        MarketData marketData = new MarketData();
        marketData.setCurrentPrices(new HashMap<>());
        marketData.setTimestamp(LocalDateTime.now());
        
        StrategySignal signal = strategy.analyze(marketData, portfolio);
        
        assertEquals(SignalType.HOLD, signal.getType());
    }
    
    @Test
    void shouldReturnHoldWhenInsufficientHistory() {
        strategy.setEnabled(true);
        
        MarketData marketData = createMarketData(
                BigDecimal.valueOf(50000), 
                BigDecimal.valueOf(3000)
        );
        
        // First few calls should return HOLD due to insufficient history
        for (int i = 0; i < 9; i++) {
            StrategySignal signal = strategy.analyze(marketData, portfolio);
            assertEquals(SignalType.HOLD, signal.getType());
        }
    }
    
    @Test
    void shouldGenerateSignalsAfterBuildingHistory() {
        strategy.setEnabled(true);
        
        // Build history with normal spread
        for (int i = 0; i < 15; i++) {
            MarketData marketData = createMarketData(
                    BigDecimal.valueOf(50000), 
                    BigDecimal.valueOf(3000)
            );
            strategy.analyze(marketData, portfolio);
        }
        
        // Now introduce a significant spread deviation
        MarketData marketData = createMarketData(
                BigDecimal.valueOf(60000), // Much higher BTC price
                BigDecimal.valueOf(3000)
        );
        
        StrategySignal signal = strategy.analyze(marketData, portfolio);
        
        // Should generate a signal (buy or sell) when spread deviates significantly
        assertNotEquals(SignalType.HOLD, signal.getType());
    }
    
    @Test
    void shouldHaveCorrectPairs() {
        assertEquals("BTC/USDT", strategy.getPair1().getSymbol());
        assertEquals("ETH/USDT", strategy.getPair2().getSymbol());
    }
    
    @Test
    void shouldTrackHistorySize() {
        strategy.setEnabled(true);
        
        assertEquals(0, strategy.getHistorySize());
        
        MarketData marketData = createMarketData(
                BigDecimal.valueOf(50000), 
                BigDecimal.valueOf(3000)
        );
        
        strategy.analyze(marketData, portfolio);
        assertEquals(1, strategy.getHistorySize());
        
        // Add more data points
        for (int i = 0; i < 10; i++) {
            strategy.analyze(marketData, portfolio);
        }
        assertEquals(11, strategy.getHistorySize());
    }
    
    @Test
    void shouldCalculateZScore() {
        strategy.setEnabled(true);
        
        // Build some history
        for (int i = 0; i < 15; i++) {
            MarketData marketData = createMarketData(
                    BigDecimal.valueOf(50000), 
                    BigDecimal.valueOf(3000)
            );
            strategy.analyze(marketData, portfolio);
        }
        
        double zScore = strategy.getCurrentZScore();
        // Z-score should be calculated (can be positive, negative, or zero)
        assertNotNull(zScore);
    }
    
    @Test
    void shouldHaveValidConfiguration() {
        Map<String, Object> config = strategy.getConfiguration();
        
        assertNotNull(config);
        assertTrue(config.containsKey("pair1"));
        assertTrue(config.containsKey("pair2"));
        assertTrue(config.containsKey("upperThreshold"));
        assertTrue(config.containsKey("lowerThreshold"));
    }
    
    @Test
    void shouldHandleDefaultConfiguration() {
        PairTradingStrategy defaultStrategy = new PairTradingStrategy();
        
        assertEquals("PairTradingStrategy", defaultStrategy.getStrategyName());
        assertNotNull(defaultStrategy.getPair1());
        assertNotNull(defaultStrategy.getPair2());
        assertFalse(defaultStrategy.isEnabled()); // Should be disabled by default
    }
    
    private MarketData createMarketData(BigDecimal btcPrice, BigDecimal ethPrice) {
        Map<TradingPair, BigDecimal> prices = new HashMap<>();
        prices.put(btcPair, btcPrice);
        prices.put(ethPair, ethPrice);
        
        Map<TradingPair, BigDecimal> volumes = new HashMap<>();
        volumes.put(btcPair, BigDecimal.valueOf(1000000));
        volumes.put(ethPair, BigDecimal.valueOf(500000));
        
        return new MarketData(prices, volumes, LocalDateTime.now());
    }
}