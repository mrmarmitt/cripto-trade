package com.marmitt.ctrade.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class PriceCacheHistoryServiceTest {
    
    private PriceCacheService cacheService;
    
    @BeforeEach
    void setUp() {
        cacheService = new PriceCacheService(5, 10); // 5 min TTL, max 10 entries
    }
    
    @Test
    void shouldStoreMultiplePricesForSameTradingPair() {
        String tradingPair = "BTC/USD";
        LocalDateTime now = LocalDateTime.now();
        
        cacheService.updatePrice(tradingPair, new BigDecimal("50000"), now.minusMinutes(3));
        cacheService.updatePrice(tradingPair, new BigDecimal("50500"), now.minusMinutes(2));
        cacheService.updatePrice(tradingPair, new BigDecimal("51000"), now.minusMinutes(1));
        
        List<PriceCacheService.PriceCacheEntry> history = cacheService.getPriceHistory(tradingPair);
        
        assertThat(history).hasSize(3);
        assertThat(history.get(0).price()).isEqualByComparingTo(new BigDecimal("50000"));
        assertThat(history.get(1).price()).isEqualByComparingTo(new BigDecimal("50500"));
        assertThat(history.get(2).price()).isEqualByComparingTo(new BigDecimal("51000"));
    }
    
    @Test
    void shouldReturnLatestValidPrice() {
        String tradingPair = "BTC/USD";
        LocalDateTime now = LocalDateTime.now();
        
        cacheService.updatePrice(tradingPair, new BigDecimal("50000"), now.minusMinutes(3));
        cacheService.updatePrice(tradingPair, new BigDecimal("51000"), now.minusMinutes(1)); // Mais recente
        
        Optional<BigDecimal> latestPrice = cacheService.getLatestPrice(tradingPair);
        
        assertThat(latestPrice).isPresent();
        assertThat(latestPrice.get()).isEqualByComparingTo(new BigDecimal("51000"));
    }
    
    @Test
    void shouldLimitHistorySize() {
        String tradingPair = "BTC/USD";
        LocalDateTime now = LocalDateTime.now();
        
        // Adicionar mais entradas do que o limite máximo (10)
        // Usar timestamps válidos (dentro do TTL de 5 minutos)
        for (int i = 0; i < 15; i++) {
            BigDecimal price = new BigDecimal(50000 + i * 100);
            cacheService.updatePrice(tradingPair, price, now.minusSeconds(i * 10)); // 10 segundos de diferença entre cada entrada
        }
        
        List<PriceCacheService.PriceCacheEntry> history = cacheService.getPriceHistory(tradingPair);
        
        assertThat(history).hasSize(10); // Limitado a 10 entradas
        
        // Deve manter as 10 mais recentes (entradas 5 até 14)
        assertThat(history.get(0).price()).isEqualByComparingTo(new BigDecimal("50500")); // Entry 5
        assertThat(history.get(9).price()).isEqualByComparingTo(new BigDecimal("51400")); // Entry 14
    }
    
    @Test
    void shouldGetLimitedHistory() {
        String tradingPair = "BTC/USD";
        LocalDateTime now = LocalDateTime.now();
        
        for (int i = 0; i < 8; i++) {
            BigDecimal price = new BigDecimal(50000 + i * 100);
            cacheService.updatePrice(tradingPair, price, now.minusMinutes(8 - i));
        }
        
        List<PriceCacheService.PriceCacheEntry> last3 = cacheService.getPriceHistory(tradingPair, 3);
        
        assertThat(last3).hasSize(3);
        assertThat(last3.get(0).price()).isEqualByComparingTo(new BigDecimal("50500")); // 3 últimas
        assertThat(last3.get(1).price()).isEqualByComparingTo(new BigDecimal("50600"));
        assertThat(last3.get(2).price()).isEqualByComparingTo(new BigDecimal("50700"));
    }
    
    @Test
    void shouldFilterExpiredEntriesFromHistory() {
        PriceCacheService shortTtlCache = new PriceCacheService(2, 10); // 2 min TTL
        String tradingPair = "BTC/USD";
        LocalDateTime now = LocalDateTime.now();
        
        shortTtlCache.updatePrice(tradingPair, new BigDecimal("50000"), now.minusMinutes(5)); // Expirado
        shortTtlCache.updatePrice(tradingPair, new BigDecimal("51000"), now.minusMinutes(1)); // Válido
        
        List<PriceCacheService.PriceCacheEntry> history = shortTtlCache.getPriceHistory(tradingPair);
        
        assertThat(history).hasSize(1); // Apenas o válido
        assertThat(history.get(0).price()).isEqualByComparingTo(new BigDecimal("51000"));
    }
    
    @Test
    void shouldCountTotalHistoryEntries() {
        LocalDateTime now = LocalDateTime.now();
        
        cacheService.updatePrice("BTC/USD", new BigDecimal("50000"), now);
        cacheService.updatePrice("BTC/USD", new BigDecimal("50100"), now);
        cacheService.updatePrice("ETH/USD", new BigDecimal("3000"), now);
        
        assertThat(cacheService.getCacheSize()).isEqualTo(2); // 2 trading pairs
        assertThat(cacheService.getTotalHistoryEntries()).isEqualTo(3); // 3 entries total
    }
    
    @Test
    void shouldClearExpiredEntriesFromHistory() {
        PriceCacheService shortTtlCache = new PriceCacheService(2, 10); // 2 min TTL
        LocalDateTime now = LocalDateTime.now();
        
        shortTtlCache.updatePrice("BTC/USD", new BigDecimal("50000"), now.minusMinutes(5)); // Expirado
        shortTtlCache.updatePrice("BTC/USD", new BigDecimal("50100"), now.minusMinutes(1)); // Válido
        shortTtlCache.updatePrice("ETH/USD", new BigDecimal("3000"), now.minusMinutes(5)); // Expirado
        
        int removedCount = shortTtlCache.clearExpiredEntries();
        
        assertThat(removedCount).isEqualTo(2); // 2 entradas expiradas removidas
        assertThat(shortTtlCache.getCacheSize()).isEqualTo(1); // BTC/USD ainda tem entrada válida
        assertThat(shortTtlCache.getTotalHistoryEntries()).isEqualTo(1); // ETH/USD removido completamente
    }
    
    @Test
    void shouldReturnEmptyHistoryForNonExistentPair() {
        List<PriceCacheService.PriceCacheEntry> history = cacheService.getPriceHistory("UNKNOWN/USD");
        
        assertThat(history).isEmpty();
    }
}