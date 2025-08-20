package com.marmitt.ctrade.application.service;

import com.marmitt.ctrade.domain.valueobject.StrategyMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PerformanceService {

    private final StrategyPerformanceTracker performanceTracker;
    private final TradingOrchestrator tradingOrchestrator;

    public StrategyMetrics getStrategyMetrics(String strategyName) {
        StrategyMetrics metrics = performanceTracker.calculateMetrics(strategyName);
        
        if (metrics == null) {
            log.warn("PERF_VALIDATION: No metrics found for strategy={}", strategyName);
            return null;
        }
        
        log.info("PERF_VALIDATION: strategy={} totalPnL={} trades={} winRate={}", 
                strategyName, metrics.getTotalPnL(), metrics.getTotalTrades(), 
                metrics.getWinRate());
        
        return metrics;
    }

    public List<StrategyMetrics> getAllStrategiesMetrics() {
        List<StrategyMetrics> allMetrics = performanceTracker.getAllStrategiesMetrics();
        
        BigDecimal totalPnL = allMetrics.stream()
                .map(StrategyMetrics::getTotalPnL)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        long totalTrades = allMetrics.stream()
                .mapToLong(StrategyMetrics::getTotalTrades)
                .sum();
        
        log.info("PERF_VALIDATION: allStrategies count={} totalPnL={} totalTrades={}", 
                allMetrics.size(), totalPnL, totalTrades);
        
        return allMetrics;
    }

    public PerformanceSummary getPerformanceSummary() {
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
        
        log.info("PERF_VALIDATION: summary realizedPnL={} unrealizedPnL={} totalPnL={} best={} worst={} activeStrategies={}", 
                totalRealizedPnL, totalUnrealizedPnL, totalPnL, bestStrategy, worstStrategy, topStrategies.size());
        
        return summary;
    }

    public void updateUnrealizedPnL() {
        log.info("PERF_VALIDATION: Manual unrealized P&L update initiated");
        tradingOrchestrator.updateUnrealizedPnL();
        log.info("PERF_VALIDATION: Manual unrealized P&L update completed");
    }

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