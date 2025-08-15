package com.marmitt.ctrade.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class PriceCacheServiceTest {
    
    private PriceCacheService priceCacheService;
    
    @BeforeEach
    void setUp() {
        priceCacheService = new PriceCacheService(5, 100); // 5 min TTL, max 100 entries
    }
    
    @Test
    void shouldUpdateAndRetrievePrice() {
        String tradingPair = "BTC/USD";
        BigDecimal price = new BigDecimal("50000.00");
        LocalDateTime timestamp = LocalDateTime.now();
        
        priceCacheService.updatePrice(tradingPair, price, timestamp);
        
        Optional<BigDecimal> cachedPrice = priceCacheService.getLatestPrice(tradingPair);
        Optional<LocalDateTime> lastUpdate = priceCacheService.getLastUpdateTime(tradingPair);
        
        assertThat(cachedPrice).isPresent();
        assertThat(cachedPrice.get()).isEqualByComparingTo(price);
        assertThat(lastUpdate).isPresent();
        assertThat(lastUpdate.get()).isEqualTo(timestamp);
        assertThat(priceCacheService.hasPrice(tradingPair)).isTrue();
    }
    
    @Test
    void shouldReturnEmptyForNonExistentTradingPair() {
        Optional<BigDecimal> price = priceCacheService.getLatestPrice("ETH/USD");
        Optional<LocalDateTime> timestamp = priceCacheService.getLastUpdateTime("ETH/USD");
        
        assertThat(price).isEmpty();
        assertThat(timestamp).isEmpty();
        assertThat(priceCacheService.hasPrice("ETH/USD")).isFalse();
    }
    
    @Test
    void shouldStoreHistoryAndReturnLatestPrice() {
        String tradingPair = "BTC/USD";
        BigDecimal oldPrice = new BigDecimal("50000.00");
        BigDecimal newPrice = new BigDecimal("51000.00");
        LocalDateTime oldTime = LocalDateTime.now();
        LocalDateTime newTime = oldTime.plusMinutes(1);
        
        priceCacheService.updatePrice(tradingPair, oldPrice, oldTime);
        priceCacheService.updatePrice(tradingPair, newPrice, newTime);
        
        // Deve retornar o preço mais recente
        Optional<BigDecimal> cachedPrice = priceCacheService.getLatestPrice(tradingPair);
        Optional<LocalDateTime> lastUpdate = priceCacheService.getLastUpdateTime(tradingPair);
        
        assertThat(cachedPrice).isPresent();
        assertThat(cachedPrice.get()).isEqualByComparingTo(newPrice);
        assertThat(lastUpdate).isPresent();
        assertThat(lastUpdate.get()).isEqualTo(newTime);
        
        // Deve ter 2 entradas no histórico
        assertThat(priceCacheService.getPriceHistory(tradingPair)).hasSize(2);
    }
    
    @Test
    void shouldClearCache() {
        priceCacheService.updatePrice("BTC/USD", new BigDecimal("50000"), LocalDateTime.now());
        priceCacheService.updatePrice("ETH/USD", new BigDecimal("3000"), LocalDateTime.now());
        
        assertThat(priceCacheService.getCacheSize()).isEqualTo(2);
        assertThat(priceCacheService.getTotalHistoryEntries()).isEqualTo(2);
        
        priceCacheService.clearCache();
        
        assertThat(priceCacheService.getCacheSize()).isZero();
        assertThat(priceCacheService.getTotalHistoryEntries()).isZero();
        assertThat(priceCacheService.hasPrice("BTC/USD")).isFalse();
        assertThat(priceCacheService.hasPrice("ETH/USD")).isFalse();
    }
    
    @Test
    void shouldHandleInvalidParameters() {
        int initialSize = priceCacheService.getCacheSize();
        int initialTotalEntries = priceCacheService.getTotalHistoryEntries();
        
        priceCacheService.updatePrice(null, new BigDecimal("50000"), LocalDateTime.now());
        priceCacheService.updatePrice("BTC/USD", null, LocalDateTime.now());
        priceCacheService.updatePrice("BTC/USD", new BigDecimal("50000"), null);
        
        assertThat(priceCacheService.getCacheSize()).isEqualTo(initialSize);
        assertThat(priceCacheService.getTotalHistoryEntries()).isEqualTo(initialTotalEntries);
    }
}