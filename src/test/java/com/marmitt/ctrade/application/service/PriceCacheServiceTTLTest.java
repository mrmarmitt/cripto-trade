package com.marmitt.ctrade.application.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class PriceCacheServiceTTLTest {
    
    @Test
    void shouldReturnValidPriceWithinTTL() {
        PriceCacheService cacheService = new PriceCacheService(5, 100); // 5 minutes TTL, max 100 entries
        String tradingPair = "BTC/USD";
        BigDecimal price = new BigDecimal("50000.00");
        LocalDateTime recentTime = LocalDateTime.now().minusMinutes(2); // 2 minutes ago
        
        cacheService.updatePrice(tradingPair, price, recentTime);
        
        Optional<BigDecimal> cachedPrice = cacheService.getLatestPrice(tradingPair);
        
        assertThat(cachedPrice).isPresent();
        assertThat(cachedPrice.get()).isEqualByComparingTo(price);
        assertThat(cacheService.hasPrice(tradingPair)).isTrue();
    }
    
    @Test
    void shouldReturnEmptyForExpiredPrice() {
        PriceCacheService cacheService = new PriceCacheService(5, 100); // 5 minutes TTL, max 100 entries
        String tradingPair = "BTC/USD";
        BigDecimal price = new BigDecimal("50000.00");
        LocalDateTime expiredTime = LocalDateTime.now().minusMinutes(10); // 10 minutes ago (expired)
        
        cacheService.updatePrice(tradingPair, price, expiredTime);
        
        Optional<BigDecimal> cachedPrice = cacheService.getLatestPrice(tradingPair);
        
        assertThat(cachedPrice).isEmpty();
        assertThat(cacheService.hasPrice(tradingPair)).isFalse();
    }
    
    @Test
    void shouldRemoveExpiredEntryOnAccess() {
        PriceCacheService cacheService = new PriceCacheService(5, 100); // 5 minutes TTL, max 100 entries
        String tradingPair = "BTC/USD";
        BigDecimal price = new BigDecimal("50000.00");
        LocalDateTime expiredTime = LocalDateTime.now().minusMinutes(10); // 10 minutes ago
        
        cacheService.updatePrice(tradingPair, price, expiredTime);
        
        // Cache should have 1 trading pair with 1 entry before access
        assertThat(cacheService.getCacheSize()).isEqualTo(1);
        assertThat(cacheService.getTotalHistoryEntries()).isEqualTo(1);
        
        // Access should return empty for expired entry
        Optional<BigDecimal> result = cacheService.getLatestPrice(tradingPair);
        assertThat(result).isEmpty();
        
        // Trading pair still exists but has no valid entries
        assertThat(cacheService.getCacheSize()).isEqualTo(1);
        assertThat(cacheService.getTotalHistoryEntries()).isEqualTo(1);
        assertThat(cacheService.hasPrice(tradingPair)).isFalse();
    }
    
    @Test
    void shouldClearExpiredEntriesBulk() {
        PriceCacheService cacheService = new PriceCacheService(5, 100); // 5 minutes TTL, max 100 entries
        LocalDateTime validTime = LocalDateTime.now().minusMinutes(2);
        LocalDateTime expiredTime = LocalDateTime.now().minusMinutes(10);
        
        // Add mix of valid and expired entries
        cacheService.updatePrice("BTC/USD", new BigDecimal("50000"), validTime);
        cacheService.updatePrice("ETH/USD", new BigDecimal("3000"), expiredTime);
        cacheService.updatePrice("LTC/USD", new BigDecimal("150"), expiredTime);
        cacheService.updatePrice("ADA/USD", new BigDecimal("0.5"), validTime);
        
        assertThat(cacheService.getCacheSize()).isEqualTo(4); // 4 trading pairs
        assertThat(cacheService.getTotalHistoryEntries()).isEqualTo(4); // 4 total entries
        
        int removedCount = cacheService.clearExpiredEntries();
        
        assertThat(removedCount).isEqualTo(2); // ETH/USD and LTC/USD entries should be removed
        assertThat(cacheService.getCacheSize()).isEqualTo(2); // BTC/USD and ADA/USD should remain (ETH/USD and LTC/USD removed completely)
        assertThat(cacheService.getTotalHistoryEntries()).isEqualTo(2); // 2 valid entries remain
        assertThat(cacheService.hasPrice("BTC/USD")).isTrue();
        assertThat(cacheService.hasPrice("ADA/USD")).isTrue();
        assertThat(cacheService.hasPrice("ETH/USD")).isFalse();
        assertThat(cacheService.hasPrice("LTC/USD")).isFalse();
    }
    
    @Test
    void shouldConfigureTTLFromConstructor() {
        PriceCacheService shortTtlCache = new PriceCacheService(1, 100); // 1 minute TTL, max 100 entries
        String tradingPair = "BTC/USD";
        BigDecimal price = new BigDecimal("50000.00");
        LocalDateTime time = LocalDateTime.now().minusMinutes(2); // 2 minutes ago
        
        shortTtlCache.updatePrice(tradingPair, price, time);
        
        // Should be expired with 1-minute TTL
        Optional<BigDecimal> cachedPrice = shortTtlCache.getLatestPrice(tradingPair);
        assertThat(cachedPrice).isEmpty();
    }
    
    @Test
    void shouldReturnZeroWhenNoExpiredEntries() {
        PriceCacheService cacheService = new PriceCacheService(5, 100); // 5 minutes TTL, max 100 entries
        LocalDateTime validTime = LocalDateTime.now().minusMinutes(2);
        
        cacheService.updatePrice("BTC/USD", new BigDecimal("50000"), validTime);
        cacheService.updatePrice("ETH/USD", new BigDecimal("3000"), validTime);
        
        int removedCount = cacheService.clearExpiredEntries();
        
        assertThat(removedCount).isZero();
        assertThat(cacheService.getCacheSize()).isEqualTo(2);
    }
}