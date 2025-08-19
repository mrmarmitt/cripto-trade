package com.marmitt.ctrade.controller;

import com.marmitt.ctrade.application.service.StrategyPerformanceTracker;
import com.marmitt.ctrade.application.service.TradingOrchestrator;
import com.marmitt.ctrade.domain.valueobject.StrategyMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * Controller REST para consulta de performance e P&L das estratégias
 */
@Slf4j
@RestController
@RequestMapping("/api/performance")
@RequiredArgsConstructor
public class PerformanceController {
    
    private final StrategyPerformanceTracker performanceTracker;
    private final TradingOrchestrator tradingOrchestrator;
    
    /**
     * GET /api/performance/strategies/{strategyName}
     * Retorna métricas completas de uma estratégia específica
     */
    @GetMapping("/strategies/{strategyName}")
    public ResponseEntity<StrategyMetrics> getStrategyMetrics(@PathVariable String strategyName) {
        try {
            log.info("Requested performance metrics for strategy: {}", strategyName);
            
            StrategyMetrics metrics = performanceTracker.calculateMetrics(strategyName);
            
            if (metrics == null) {
                log.warn("No metrics found for strategy: {}", strategyName);
                return ResponseEntity.notFound().build();
            }
            
            log.debug("Returning metrics for {}: Total P&L = {}, Trades = {}", 
                    strategyName, metrics.getTotalPnL(), metrics.getTotalTrades());
            
            return ResponseEntity.ok(metrics);
            
        } catch (Exception e) {
            log.error("Error retrieving metrics for strategy {}: {}", strategyName, e.getMessage(), e);
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
            log.info("Requested performance metrics for all strategies");
            
            List<StrategyMetrics> allMetrics = performanceTracker.getAllStrategiesMetrics();
            
            log.debug("Returning metrics for {} strategies", allMetrics.size());
            
            return ResponseEntity.ok(allMetrics);
            
        } catch (Exception e) {
            log.error("Error retrieving metrics for all strategies: {}", e.getMessage(), e);
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
            log.info("Requested performance summary");
            
            BigDecimal totalRealizedPnL = performanceTracker.getTotalRealizedPnL();
            BigDecimal totalUnrealizedPnL = performanceTracker.getTotalUnrealizedPnL();
            BigDecimal totalPnL = performanceTracker.getTotalPnL();
            
            String bestStrategy = performanceTracker.getBestStrategy();
            String worstStrategy = performanceTracker.getWorstStrategy();
            
            List<StrategyMetrics> topStrategies = performanceTracker.getTopPerformingStrategies(5);
            
            PerformanceSummary summary = new PerformanceSummary(
                    totalRealizedPnL,
                    totalUnrealizedPnL, 
                    totalPnL,
                    bestStrategy,
                    worstStrategy,
                    topStrategies.size(),
                    topStrategies
            );
            
            log.debug("Performance summary: Total P&L = {}, Best = {}, Worst = {}", 
                    totalPnL, bestStrategy, worstStrategy);
            
            return ResponseEntity.ok(summary);
            
        } catch (Exception e) {
            log.error("Error retrieving performance summary: {}", e.getMessage(), e);
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
            log.info("Manual update of unrealized P&L requested");
            
            tradingOrchestrator.updateUnrealizedPnL();
            
            return ResponseEntity.ok("Unrealized P&L updated successfully");
            
        } catch (Exception e) {
            log.error("Error updating unrealized P&L: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * DTO para resumo de performance
     */
    public static record PerformanceSummary(
            BigDecimal totalRealizedPnL,
            BigDecimal totalUnrealizedPnL,
            BigDecimal totalPnL,
            String bestStrategy,
            String worstStrategy,
            int activeStrategies,
            List<StrategyMetrics> topPerformers
    ) {}
}