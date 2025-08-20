package com.marmitt.ctrade.controller;

import com.marmitt.ctrade.application.service.PerformanceService;
import com.marmitt.ctrade.application.service.PerformanceService.PerformanceSummary;
import com.marmitt.ctrade.domain.valueobject.StrategyMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller REST para consulta de performance e P&L das estratégias
 */
@Slf4j
@RestController
@RequestMapping("/api/performance")
@RequiredArgsConstructor
public class PerformanceController {
    
    private final PerformanceService performanceService;
    
    /**
     * GET /api/performance/strategies/{strategyName}
     * Retorna métricas completas de uma estratégia específica
     */
    @GetMapping("/strategies/{strategyName}")
    public ResponseEntity<StrategyMetrics> getStrategyMetrics(@PathVariable String strategyName) {
        try {
            StrategyMetrics metrics = performanceService.getStrategyMetrics(strategyName);
            
            if (metrics == null) {
                return ResponseEntity.notFound().build();
            }
            
            return ResponseEntity.ok(metrics);
            
        } catch (Exception e) {
            log.error("PERF_ERROR: Failed to get metrics for strategy={} error={}", strategyName, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * GET /api/performance/strategies
     * Retorna métricas de todas as estratégias
     */
    @GetMapping("/strategies")
    public ResponseEntity<List<StrategyMetrics>> getAllStrategiesMetrics() {
        try {
            List<StrategyMetrics> allMetrics = performanceService.getAllStrategiesMetrics();
            return ResponseEntity.ok(allMetrics);
            
        } catch (Exception e) {
            log.error("PERF_ERROR: Failed to get all strategies metrics error={}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * GET /api/performance/summary
     * Retorna resumo geral de P&L
     */
    @GetMapping("/summary")
    public ResponseEntity<PerformanceSummary> getPerformanceSummary() {
        try {
            PerformanceSummary summary = performanceService.getPerformanceSummary();
            return ResponseEntity.ok(summary);
            
        } catch (Exception e) {
            log.error("PERF_ERROR: Failed to get performance summary error={}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * POST /api/performance/update-unrealized
     * Força atualização de P&L não realizado
     */
    @PostMapping("/update-unrealized")
    public ResponseEntity<String> updateUnrealizedPnL() {
        try {
            performanceService.updateUnrealizedPnL();
            return ResponseEntity.ok("Unrealized P&L updated successfully");
            
        } catch (Exception e) {
            log.error("PERF_ERROR: Failed to update unrealized P&L error={}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
    
}