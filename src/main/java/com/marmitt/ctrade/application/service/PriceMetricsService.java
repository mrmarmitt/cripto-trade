package com.marmitt.ctrade.application.service;

import com.marmitt.ctrade.controller.dto.SystemMetricsSummary;
import com.marmitt.ctrade.domain.entity.PriceMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class PriceMetricsService {
    
    private final Map<String, PriceMetrics> metricsByPair = new ConcurrentHashMap<>();
    
    public void recordPriceUpdate(String tradingPair, BigDecimal price, LocalDateTime timestamp) {
        PriceMetrics metrics = metricsByPair.computeIfAbsent(tradingPair, PriceMetrics::new);
        metrics.updatePrice(price, timestamp);
        
        log.debug("Price metrics updated for {}: {} updates, avg: {}", 
                tradingPair, 
                metrics.getUpdateCount().get(), 
                metrics.getAveragePrice());
    }
    
    public PriceMetrics getMetrics(String tradingPair) {
        return metricsByPair.get(tradingPair);
    }
    
    public Collection<PriceMetrics> getAllMetrics() {
        return metricsByPair.values();
    }
    
    public int getTotalUpdateCount() {
        return metricsByPair.values().stream()
                           .mapToInt(metrics -> metrics.getUpdateCount().get())
                           .sum();
    }
    
    public double getSystemAverageVolatility() {
        return metricsByPair.values().stream()
                           .mapToDouble(PriceMetrics::getVolatility)
                           .average()
                           .orElse(0.0);
    }
    
    public SystemMetricsSummary getSystemMetricsSummary() {
        return new SystemMetricsSummary(
            metricsByPair.size(),
            getTotalUpdateCount(),
            getSystemAverageVolatility(),
            metricsByPair.isEmpty() ? null : 
                metricsByPair.values().stream()
                    .min((m1, m2) -> m1.getFirstUpdateTime().compareTo(m2.getFirstUpdateTime()))
                    .map(PriceMetrics::getFirstUpdateTime)
                    .orElse(null),
            metricsByPair.isEmpty() ? null :
                metricsByPair.values().stream()
                    .max((m1, m2) -> m1.getLastUpdateTime().compareTo(m2.getLastUpdateTime()))
                    .map(PriceMetrics::getLastUpdateTime)
                    .orElse(null)
        );
    }
    
    public void resetMetrics(String tradingPair) {
        metricsByPair.remove(tradingPair);
        log.info("Metrics reset for trading pair: {}", tradingPair);
    }
    
    public void resetAllMetrics() {
        int count = metricsByPair.size();
        metricsByPair.clear();
        log.info("All metrics reset. {} pairs cleared", count);
    }
}