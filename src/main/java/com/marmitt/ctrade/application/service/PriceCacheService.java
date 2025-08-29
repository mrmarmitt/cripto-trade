package com.marmitt.ctrade.application.service;

import com.marmitt.ctrade.config.TradingProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class PriceCacheService {
    
    private final Map<String, List<PriceCacheEntry>> priceHistoryCache = new ConcurrentHashMap<>();
    private final TradingProperties tradingProperties;
    
    public PriceCacheService(TradingProperties tradingProperties) {

        this.tradingProperties = tradingProperties;
    }
    
    public void updatePrice(String tradingPair, BigDecimal price, LocalDateTime timestamp) {
        if (tradingPair == null || price == null || timestamp == null) {
            log.warn("Invalid price update parameters: tradingPair={}, price={}, timestamp={}", 
                    tradingPair, price, timestamp);
            return;
        }
        
        PriceCacheEntry entry = new PriceCacheEntry(price, timestamp);
        
        priceHistoryCache.computeIfAbsent(tradingPair, k -> new ArrayList<>()).add(entry);
        
        List<PriceCacheEntry> history = priceHistoryCache.get(tradingPair);
        
        // Limitar tamanho do histórico
        if (history.size() > tradingProperties.priceCache().maxHistorySize()) {
            history.remove(0); // Remove o mais antigo
            log.debug("Removed oldest price entry for {} to maintain max history size", tradingPair);
        }
        
        log.debug("Price added to history cache: {} = {} at {} (history size: {})", 
                tradingPair, price, timestamp, history.size());
    }
    
    public Optional<BigDecimal> getLatestPrice(String tradingPair) {
        List<PriceCacheEntry> history = priceHistoryCache.get(tradingPair);
        if (history == null || history.isEmpty()) {
            return Optional.empty();
        }
        
        // Buscar a entrada mais recente válida (de trás para frente)
        for (int i = history.size() - 1; i >= 0; i--) {
            PriceCacheEntry entry = history.get(i);
            if (isEntryValid(entry)) {
                return Optional.of(entry.price);
            }
        }
        
        // Todas as entradas estão expiradas
        return Optional.empty();
    }

    public int getCacheSize() {
        return priceHistoryCache.size();
    }
    
    public int getTotalHistoryEntries() {
        return priceHistoryCache.values().stream()
                .mapToInt(List::size)
                .sum();
    }

    public int clearExpiredEntries() {
        int totalRemoved = 0;
        
        for (Map.Entry<String, List<PriceCacheEntry>> cacheEntry : priceHistoryCache.entrySet()) {
            String tradingPair = cacheEntry.getKey();
            List<PriceCacheEntry> history = cacheEntry.getValue();
            
            int originalSize = history.size();
            history.removeIf(entry -> !isEntryValid(entry));
            int removedCount = originalSize - history.size();
            
            if (removedCount > 0) {
                log.debug("Removed {} expired entries for {}", removedCount, tradingPair);
                totalRemoved += removedCount;
            }
            
            // Se não restaram entradas válidas, remove o trading pair do cache
            if (history.isEmpty()) {
                priceHistoryCache.remove(tradingPair);
                log.debug("Removed empty history for: {}", tradingPair);
            }
        }
        
        if (totalRemoved > 0) {
            log.info("Cleared {} expired price entries from cache", totalRemoved);
        }
        return totalRemoved;
    }
    
    private boolean isEntryValid(PriceCacheEntry entry) {
        return Duration.between(entry.timestamp, LocalDateTime.now()).compareTo(tradingProperties.priceCache().getTtlMinutesAsDuration()) < 0;
    }

    public record PriceCacheEntry(BigDecimal price, LocalDateTime timestamp) {
    }
}