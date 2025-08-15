package com.marmitt.ctrade.application.service;

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
    private final Duration cacheTtl;
    private final int maxHistorySize;
    
    public PriceCacheService(@Value("${trading.price-cache.ttl-minutes:5}") int ttlMinutes,
                            @Value("${trading.price-cache.max-history-size:100}") int maxHistorySize) {
        this.cacheTtl = Duration.ofMinutes(ttlMinutes);
        this.maxHistorySize = maxHistorySize;
        log.info("Price cache TTL configured to {} minutes, max history size: {}", ttlMinutes, maxHistorySize);
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
        if (history.size() > maxHistorySize) {
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
    
    public Optional<LocalDateTime> getLastUpdateTime(String tradingPair) {
        List<PriceCacheEntry> history = priceHistoryCache.get(tradingPair);
        if (history == null || history.isEmpty()) {
            return Optional.empty();
        }
        
        // Buscar a entrada mais recente válida
        for (int i = history.size() - 1; i >= 0; i--) {
            PriceCacheEntry entry = history.get(i);
            if (isEntryValid(entry)) {
                return Optional.of(entry.timestamp);
            }
        }
        
        return Optional.empty();
    }
    
    public boolean hasPrice(String tradingPair) {
        return getLatestPrice(tradingPair).isPresent();
    }
    
    public List<PriceCacheEntry> getPriceHistory(String tradingPair) {
        List<PriceCacheEntry> history = priceHistoryCache.get(tradingPair);
        if (history == null) {
            return new ArrayList<>();
        }
        
        // Retornar apenas entradas válidas (não expiradas)
        return history.stream()
                .filter(this::isEntryValid)
                .toList();
    }
    
    public List<PriceCacheEntry> getPriceHistory(String tradingPair, int limit) {
        List<PriceCacheEntry> fullHistory = getPriceHistory(tradingPair);
        
        if (fullHistory.size() <= limit) {
            return fullHistory;
        }
        
        // Retornar os 'limit' mais recentes
        return fullHistory.subList(fullHistory.size() - limit, fullHistory.size());
    }
    
    public int getCacheSize() {
        return priceHistoryCache.size();
    }
    
    public int getTotalHistoryEntries() {
        return priceHistoryCache.values().stream()
                .mapToInt(List::size)
                .sum();
    }
    
    public void clearCache() {
        priceHistoryCache.clear();
        log.info("Price cache cleared");
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
        return Duration.between(entry.timestamp, LocalDateTime.now()).compareTo(cacheTtl) < 0;
    }

    public record PriceCacheEntry(BigDecimal price, LocalDateTime timestamp) {
    }
}