package com.marmitt.ctrade.domain.entity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MarketDataTest {
    
    private MarketData marketData;
    private TradingPair btcPair;
    private TradingPair ethPair;
    
    @BeforeEach
    void setUp() {
        btcPair = new TradingPair("BTC/USDT");
        ethPair = new TradingPair("ETH/USDT");
        
        Map<TradingPair, BigDecimal> prices = new HashMap<>();
        prices.put(btcPair, BigDecimal.valueOf(50000));
        prices.put(ethPair, BigDecimal.valueOf(3000));
        
        Map<TradingPair, BigDecimal> volumes = new HashMap<>();
        volumes.put(btcPair, BigDecimal.valueOf(1000000));
        volumes.put(ethPair, BigDecimal.valueOf(500000));
        
        LocalDateTime timestamp = LocalDateTime.now();
        
        marketData = new MarketData(prices, volumes, timestamp);
    }
    
    @Test
    void shouldReturnCorrectPriceForPair() {
        BigDecimal btcPrice = marketData.getPriceFor(btcPair);
        BigDecimal ethPrice = marketData.getPriceFor(ethPair);
        
        assertEquals(BigDecimal.valueOf(50000), btcPrice);
        assertEquals(BigDecimal.valueOf(3000), ethPrice);
    }
    
    @Test
    void shouldReturnCorrectVolumeForPair() {
        BigDecimal btcVolume = marketData.getVolumeFor(btcPair);
        BigDecimal ethVolume = marketData.getVolumeFor(ethPair);
        
        assertEquals(BigDecimal.valueOf(1000000), btcVolume);
        assertEquals(BigDecimal.valueOf(500000), ethVolume);
    }
    
    @Test
    void shouldReturnNullForNonExistentPair() {
        TradingPair nonExistentPair = new TradingPair("ADA/USDT");
        
        BigDecimal price = marketData.getPriceFor(nonExistentPair);
        BigDecimal volume = marketData.getVolumeFor(nonExistentPair);
        
        assertNull(price);
        assertNull(volume);
    }
    
    @Test
    void shouldCorrectlyCheckIfHasPriceForPair() {
        assertTrue(marketData.hasPriceFor(btcPair));
        assertTrue(marketData.hasPriceFor(ethPair));
        
        TradingPair nonExistentPair = new TradingPair("ADA/USDT");
        assertFalse(marketData.hasPriceFor(nonExistentPair));
    }
    
    @Test
    void shouldReturnFalseForPairWithNullPrice() {
        Map<TradingPair, BigDecimal> pricesWithNull = new HashMap<>();
        pricesWithNull.put(btcPair, null);
        
        MarketData marketDataWithNull = new MarketData(pricesWithNull, new HashMap<>(), LocalDateTime.now());
        
        assertFalse(marketDataWithNull.hasPriceFor(btcPair));
    }
    
    @Test
    void shouldHandleEmptyMarketData() {
        MarketData emptyMarketData = new MarketData(new HashMap<>(), new HashMap<>(), LocalDateTime.now());
        
        assertFalse(emptyMarketData.hasPriceFor(btcPair));
        assertNull(emptyMarketData.getPriceFor(btcPair));
        assertNull(emptyMarketData.getVolumeFor(btcPair));
    }
    
    @Test
    void shouldHaveTimestamp() {
        assertNotNull(marketData.getTimestamp());
        assertTrue(marketData.getTimestamp().isBefore(LocalDateTime.now().plusSeconds(1)));
    }
    
    @Test
    void shouldCreateEmptyMarketData() {
        MarketData empty = new MarketData();
        
        assertNull(empty.getCurrentPrices());
        assertNull(empty.getVolumes24h());
        assertNull(empty.getTimestamp());
    }
}