package com.marmitt.ctrade.controller;

import com.marmitt.ctrade.application.service.PriceMetricsService;
import com.marmitt.ctrade.controller.dto.SystemMetricsSummary;
import com.marmitt.ctrade.domain.entity.PriceMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;

@RestController
@RequestMapping("/api/metrics")
@RequiredArgsConstructor
@Slf4j
public class PriceMetricsController {
    
    private final PriceMetricsService priceMetricsService;
    
    @GetMapping("/summary")
    public ResponseEntity<SystemMetricsSummary> getSystemMetricsSummary() {
        SystemMetricsSummary summary = priceMetricsService.getSystemMetricsSummary();
        return ResponseEntity.ok(summary);
    }
    
    @GetMapping("/prices")
    public ResponseEntity<Collection<PriceMetrics>> getAllPriceMetrics() {
        Collection<PriceMetrics> metrics = priceMetricsService.getAllMetrics();
        return ResponseEntity.ok(metrics);
    }
    
    @GetMapping("/prices/{tradingPair}")
    public ResponseEntity<PriceMetrics> getPriceMetrics(@PathVariable String tradingPair) {
        PriceMetrics metrics = priceMetricsService.getMetrics(tradingPair);
        
        if (metrics != null) {
            return ResponseEntity.ok(metrics);
        } else {
            return ResponseEntity.notFound().build();
        }
    }
    
    @GetMapping("/prices/{tradingPair}/volatility")
    public ResponseEntity<Double> getPriceVolatility(@PathVariable String tradingPair) {
        PriceMetrics metrics = priceMetricsService.getMetrics(tradingPair);
        
        if (metrics != null) {
            return ResponseEntity.ok(metrics.getVolatility());
        } else {
            return ResponseEntity.notFound().build();
        }
    }
    
    @GetMapping("/system/volatility")
    public ResponseEntity<Double> getSystemVolatility() {
        double volatility = priceMetricsService.getSystemAverageVolatility();
        return ResponseEntity.ok(volatility);
    }
    
    @GetMapping("/system/updates-count")
    public ResponseEntity<Integer> getTotalUpdatesCount() {
        int count = priceMetricsService.getTotalUpdateCount();
        return ResponseEntity.ok(count);
    }
    
    @DeleteMapping("/prices/{tradingPair}")
    public ResponseEntity<Void> resetPairMetrics(@PathVariable String tradingPair) {
        priceMetricsService.resetMetrics(tradingPair);
        log.info("Metrics reset via API for: {}", tradingPair);
        return ResponseEntity.noContent().build();
    }
    
    @DeleteMapping("/prices")
    public ResponseEntity<Void> resetAllMetrics() {
        priceMetricsService.resetAllMetrics();
        log.info("All metrics reset via API");
        return ResponseEntity.noContent().build();
    }
}