package com.marmitt.ctrade.application.service;

import com.marmitt.ctrade.domain.entity.Trade;
import com.marmitt.ctrade.domain.valueobject.StrategyMetrics;
import com.marmitt.ctrade.domain.valueobject.TradeStatus;
import com.marmitt.ctrade.infrastructure.repository.TradeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StrategyPerformanceTracker {
    
    private final TradeRepository tradeRepository;
    private final PriceCacheService priceCacheService;

    /**
     * Calcula métricas completas de uma estratégia
     */
    public StrategyMetrics calculateMetrics(String strategyName) {
        log.debug("Calculating metrics for strategy: {}", strategyName);
        
        List<Trade> allTrades = tradeRepository.findByStrategyName(strategyName);
        List<Trade> fullyClosedTrades = allTrades.stream()
                .filter(t -> t.getStatus() == TradeStatus.CLOSED)
                .toList();
        List<Trade> partiallyClosedTrades = allTrades.stream()
                .filter(t -> t.getStatus() == TradeStatus.PARTIAL_CLOSED)
                .toList();
        List<Trade> openTrades = allTrades.stream()
                .filter(t -> t.getStatus() == TradeStatus.OPEN)
                .collect(Collectors.toList());
        
        // Consistent definitions for metrics calculations
        // closedTrades for P&L = CLOSED + PARTIAL_CLOSED (any trade with realized P&L)
        List<Trade> tradesWithRealizedPnL = allTrades.stream()
                .filter(t -> t.getStatus() == TradeStatus.CLOSED || t.getStatus() == TradeStatus.PARTIAL_CLOSED)
                .collect(Collectors.toList());
        
        StrategyMetrics.StrategyMetricsBuilder builder = StrategyMetrics.builder()
                .strategyName(strategyName);
        
        // Discriminated counts logic:
        // Total = OPEN + PARTIAL_CLOSED + CLOSED (mutually exclusive)
        // openTrades = OPEN only (no closure yet)
        // partiallyClosedTrades = PARTIAL_CLOSED (some closure)
        // closedTrades = CLOSED only (full closure)
        
        int purelyOpenTrades = openTrades.size();
        int partiallyClosedCount = partiallyClosedTrades.size();
        int fullyClosedCount = fullyClosedTrades.size();
        
        builder.totalTrades(allTrades.size())
                .openTrades(purelyOpenTrades)           // Only OPEN status
                .partiallyClosedTrades(partiallyClosedCount)  // Only PARTIAL_CLOSED status
                .closedTrades(fullyClosedCount);        // Only CLOSED status
                
        log.debug("Trade status for {}: Total={}, Open={}, Partial={}, Closed={}", 
                strategyName, allTrades.size(), purelyOpenTrades, partiallyClosedCount, fullyClosedCount);
                
        // Verification: Total should equal the sum of all statuses  
        int verification = purelyOpenTrades + partiallyClosedCount + fullyClosedCount;
        if (verification != allTrades.size()) {
            log.warn("Trade count mismatch for {}: Total={} but sum of statuses={} (Open:{}, Partial:{}, Closed:{})", 
                    strategyName, allTrades.size(), verification, purelyOpenTrades, partiallyClosedCount, fullyClosedCount);
        }
        
        // P&L calculations
        calculatePnLMetrics(builder, strategyName, allTrades, tradesWithRealizedPnL, openTrades);
        
        // Win/Loss statistics
        calculateWinLossMetrics(builder, tradesWithRealizedPnL);
        
        // Order statistics
        calculateOrderMetrics(builder);
        
        // Risk metrics
        calculateRiskMetrics(builder, allTrades, tradesWithRealizedPnL);
        
        // Time metrics
        calculateTimeMetrics(builder, allTrades, tradesWithRealizedPnL);
        
        // Best/Worst trades
        calculateBestWorstTrades(builder, tradesWithRealizedPnL);
        
        // Recent performance
        calculateRecentPerformance(builder, strategyName);
        
        StrategyMetrics metrics = builder.build();
        metrics.calculateDerivedMetrics();
        
        log.debug("Calculated metrics for {}: P&L={}, Win Rate={}%, Trades={}", 
                strategyName, metrics.getTotalPnL(), 
                metrics.getWinRate() != null ? metrics.getWinRate() * 100 : 0, 
                metrics.getTotalTrades());
        
        return metrics;
    }
    
    private void calculatePnLMetrics(StrategyMetrics.StrategyMetricsBuilder builder, 
                                   String strategyName, List<Trade> allTrades, 
                                   List<Trade> tradesWithPnL, List<Trade> openTrades) {
        
        // Realized P&L (from closed trades)
        BigDecimal realizedPnL = tradesWithPnL.stream()
                .map(Trade::getRealizedPnL)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        // Unrealized P&L (from open trades, calculated with current prices)
        BigDecimal unrealizedPnL = calculateUnrealizedPnL(openTrades);
        
        // Total P&L
        BigDecimal totalPnL = realizedPnL.add(unrealizedPnL);
        
        // Total commission
        BigDecimal totalCommission = allTrades.stream()
                .map(trade -> trade.getCommission() != null ? trade.getCommission() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        // Total invested amount
        BigDecimal totalInvested = allTrades.stream()
                .map(Trade::getInvestedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        // Total return percentage
        BigDecimal totalReturn = BigDecimal.ZERO;
        if (totalInvested.compareTo(BigDecimal.ZERO) > 0) {
            totalReturn = totalPnL.divide(totalInvested, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
        }
        
        builder.realizedPnL(realizedPnL)
                .unrealizedPnL(unrealizedPnL)
                .totalPnL(totalPnL)
                .totalCommission(totalCommission)
                .totalReturn(totalReturn);
    }
    
    private BigDecimal calculateUnrealizedPnL(List<Trade> openTrades) {
        return openTrades.stream()
                .map(this::calculateTradeUnrealizedPnL)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    
    private BigDecimal calculateTradeUnrealizedPnL(Trade trade) {
        try {
            if (trade.getEntryPrice() == null || trade.getEntryQuantity() == null) {
                return BigDecimal.ZERO;
            }
            
            String symbol = trade.getPair().getSymbol();
            Optional<BigDecimal> currentPriceOpt = priceCacheService.getLatestPrice(symbol);
            
            if (currentPriceOpt.isEmpty()) {
                log.debug("No current price available for {}, using entry price", symbol);
                return BigDecimal.ZERO;
            }
            
            BigDecimal currentPrice = currentPriceOpt.get();
            BigDecimal priceDiff = currentPrice.subtract(trade.getEntryPrice());
            
            return priceDiff.multiply(trade.getEntryQuantity());
            
        } catch (Exception e) {
            log.warn("Error calculating unrealized P&L for trade {}: {}", trade.getId(), e.getMessage());
            return BigDecimal.ZERO;
        }
    }
    
    private void calculateWinLossMetrics(StrategyMetrics.StrategyMetricsBuilder builder, List<Trade> tradesWithPnL) {
        List<Trade> winningTrades = tradesWithPnL.stream()
                .filter(Trade::isProfitable)
                .toList();
        
        List<Trade> losingTrades = tradesWithPnL.stream()
                .filter(Trade::isLoss)
                .toList();
        
        List<Trade> neutralTrades = tradesWithPnL.stream()
                .filter(trade -> !trade.isProfitable() && !trade.isLoss())
                .toList();
        
        builder.winningTrades(winningTrades.size())
                .losingTrades(losingTrades.size())
                .neutralTrades(neutralTrades.size());
        
        // Log para debug quando há trades neutros (P&L = 0)
        if (!neutralTrades.isEmpty()) {
            log.info("Found {} neutral trades (P&L = 0) for strategy '{}': {}", 
                    neutralTrades.size(), "PairTradingStrategy",
                    neutralTrades.stream().map(t -> String.format("Trade[%d]: realized=%s, unrealized=%s", 
                            t.getId(), t.getRealizedPnL(), t.getUnrealizedPnL())).toList());
        }
        
        // Average win/loss
        if (!winningTrades.isEmpty()) {
            BigDecimal avgWin = winningTrades.stream()
                    .map(Trade::getRealizedPnL)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(winningTrades.size()), 4, RoundingMode.HALF_UP);
            builder.avgWin(avgWin);
        }
        
        if (!losingTrades.isEmpty()) {
            BigDecimal avgLoss = losingTrades.stream()
                    .map(Trade::getRealizedPnL)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(losingTrades.size()), 4, RoundingMode.HALF_UP);
            builder.avgLoss(avgLoss);
        }
        
        // Average P&L per trade
        if (!tradesWithPnL.isEmpty()) {
            BigDecimal avgPnL = tradesWithPnL.stream()
                    .map(Trade::getRealizedPnL)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(tradesWithPnL.size()), 4, RoundingMode.HALF_UP);
            builder.avgPnL(avgPnL);
        }
    }
    
    private void calculateOrderMetrics(StrategyMetrics.StrategyMetricsBuilder builder) {
        try {
//            MockExchangeAdapter.OrderStatistics orderStats = mockExchangeAdapter.getOrderStatistics();
            
//            builder.totalOrders(orderStats.totalOrders)
//                    .successfulOrders(orderStats.successfulOrders)
//                    .failedOrders(orderStats.failedOrders)
//                    .pendingOrders(orderStats.pendingOrders);
                    
//            log.debug("Order statistics: Total={}, Successful={}, Failed={}, Pending={}",
//                    orderStats.totalOrders, orderStats.successfulOrders,
//                    orderStats.failedOrders, orderStats.pendingOrders);
                    
        } catch (Exception e) {
            log.warn("Error calculating order metrics: {}", e.getMessage());
            // Set default values if order statistics are not available
            builder.totalOrders(0)
                    .successfulOrders(0)
                    .failedOrders(0)
                    .pendingOrders(0);
        }
    }
    
    private void calculateRiskMetrics(StrategyMetrics.StrategyMetricsBuilder builder, 
                                    List<Trade> allTrades, List<Trade> tradesWithPnL) {
        
        // Max drawdown from trades
        BigDecimal maxDrawdown = allTrades.stream()
                .map(trade -> trade.getMaxDrawdown() != null ? trade.getMaxDrawdown() : BigDecimal.ZERO)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);
        
        builder.maxDrawdown(maxDrawdown);
        
        // Calculate Sharpe Ratio if we have enough data
        if (tradesWithPnL.size() >= 10) {
            BigDecimal sharpeRatio = calculateSharpeRatio(tradesWithPnL);
            builder.sharpeRatio(sharpeRatio);
        }
        
        // Calculate volatility
        if (tradesWithPnL.size() >= 5) {
            BigDecimal volatility = calculateVolatility(tradesWithPnL);
            builder.volatility(volatility);
        }
    }
    
    private BigDecimal calculateSharpeRatio(List<Trade> tradesWithPnL) {
        try {
            // Calculate returns
            List<BigDecimal> returns = tradesWithPnL.stream()
                    .map(Trade::getReturnPercentage)
                    .collect(Collectors.toList());
            
            // Mean return
            BigDecimal meanReturn = returns.stream()
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(returns.size()), 8, RoundingMode.HALF_UP);
            
            // Standard deviation
            BigDecimal variance = returns.stream()
                    .map(ret -> ret.subtract(meanReturn).pow(2))
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(returns.size() - 1), 8, RoundingMode.HALF_UP);
            
            double stdDev = Math.sqrt(variance.doubleValue());
            
            if (stdDev == 0.0) return BigDecimal.ZERO;
            
            // Risk-free rate (assumed 2% annually, converted to per-trade)
            double riskFreeRate = 0.02 / 365; // Daily risk-free rate
            
            double sharpe = (meanReturn.doubleValue() - riskFreeRate) / stdDev;
            
            return BigDecimal.valueOf(sharpe).setScale(4, RoundingMode.HALF_UP);
            
        } catch (Exception e) {
            log.warn("Error calculating Sharpe ratio: {}", e.getMessage());
            return BigDecimal.ZERO;
        }
    }
    
    private BigDecimal calculateVolatility(List<Trade> tradesWithPnL) {
        try {
            List<BigDecimal> returns = tradesWithPnL.stream()
                    .map(Trade::getReturnPercentage)
                    .collect(Collectors.toList());
            
            BigDecimal mean = returns.stream()
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(returns.size()), 8, RoundingMode.HALF_UP);
            
            BigDecimal variance = returns.stream()
                    .map(ret -> ret.subtract(mean).pow(2))
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(returns.size() - 1), 8, RoundingMode.HALF_UP);
            
            double volatility = Math.sqrt(variance.doubleValue());
            
            return BigDecimal.valueOf(volatility).setScale(4, RoundingMode.HALF_UP);
            
        } catch (Exception e) {
            log.warn("Error calculating volatility: {}", e.getMessage());
            return BigDecimal.ZERO;
        }
    }
    
    private void calculateTimeMetrics(StrategyMetrics.StrategyMetricsBuilder builder, 
                                    List<Trade> allTrades, List<Trade> tradesWithPnL) {
        
        if (!allTrades.isEmpty()) {
            LocalDateTime firstTrade = allTrades.stream()
                    .map(Trade::getEntryTime)
                    .filter(time -> time != null)
                    .min(LocalDateTime::compareTo)
                    .orElse(null);
            
            LocalDateTime lastTrade = allTrades.stream()
                    .map(Trade::getEntryTime)
                    .filter(time -> time != null)
                    .max(LocalDateTime::compareTo)
                    .orElse(null);
            
            builder.firstTradeDate(firstTrade)
                    .lastTradeDate(lastTrade);
            
            if (firstTrade != null && lastTrade != null) {
                Duration totalActiveTime = Duration.between(firstTrade, lastTrade);
                builder.totalActiveTime(totalActiveTime);
            }
        }
        
        // Average holding period for closed trades
        if (!tradesWithPnL.isEmpty()) {
            List<Duration> holdingPeriods = tradesWithPnL.stream()
                    .map(Trade::getHoldingPeriod)
                    .filter(duration -> !duration.isZero())
                    .collect(Collectors.toList());
            
            if (!holdingPeriods.isEmpty()) {
                long avgSeconds = holdingPeriods.stream()
                        .mapToLong(Duration::getSeconds)
                        .sum() / holdingPeriods.size();
                
                Duration avgHoldingPeriod = Duration.ofSeconds(avgSeconds);
                builder.avgHoldingPeriod(avgHoldingPeriod);
                
                Duration maxHoldingPeriod = holdingPeriods.stream()
                        .max(Duration::compareTo)
                        .orElse(Duration.ZERO);
                
                Duration minHoldingPeriod = holdingPeriods.stream()
                        .min(Duration::compareTo)
                        .orElse(Duration.ZERO);
                
                builder.maxHoldingPeriod(maxHoldingPeriod)
                        .minHoldingPeriod(minHoldingPeriod);
            }
        }
    }
    
    private void calculateBestWorstTrades(StrategyMetrics.StrategyMetricsBuilder builder, List<Trade> tradesWithPnL) {
        if (tradesWithPnL.isEmpty()) return;
        
        // Best trade (highest P&L)
        Trade bestTrade = tradesWithPnL.stream()
                .max(Comparator.comparing(Trade::getRealizedPnL))
                .orElse(null);
        
        if (bestTrade != null) {
            builder.bestTrade(bestTrade.getRealizedPnL())
                    .bestTradeReturn(bestTrade.getReturnPercentage());
        }
        
        // Worst trade (lowest P&L)
        Trade worstTrade = tradesWithPnL.stream()
                .min(Comparator.comparing(Trade::getRealizedPnL))
                .orElse(null);
        
        if (worstTrade != null) {
            builder.worstTrade(worstTrade.getRealizedPnL())
                    .worstTradeReturn(worstTrade.getReturnPercentage());
        }
    }
    
    private void calculateRecentPerformance(StrategyMetrics.StrategyMetricsBuilder builder, String strategyName) {
        // Today's P&L
        LocalDateTime dayStart = LocalDateTime.now().truncatedTo(ChronoUnit.DAYS);
        LocalDateTime dayEnd = dayStart.plusDays(1);
        BigDecimal todaysPnL = tradeRepository.sumTodaysPnL(strategyName, dayStart, dayEnd).orElse(BigDecimal.ZERO);
        builder.todaysPnL(todaysPnL);
        
        // Week P&L
        LocalDateTime weekStart = LocalDateTime.now().truncatedTo(ChronoUnit.DAYS).minusDays(7);
        BigDecimal weekPnL = tradeRepository.sumWeekPnL(strategyName, weekStart).orElse(BigDecimal.ZERO);
        builder.weekPnL(weekPnL);
        
        // Month P&L
        LocalDateTime monthStart = LocalDateTime.now().truncatedTo(ChronoUnit.DAYS).minusDays(30);
        BigDecimal monthPnL = tradeRepository.sumMonthPnL(strategyName, monthStart).orElse(BigDecimal.ZERO);
        builder.monthPnL(monthPnL);
    }
    
    /**
     * Retorna métricas de todas as estratégias
     */
    public List<StrategyMetrics> getAllStrategiesMetrics() {
        List<String> strategyNames = tradeRepository.findDistinctStrategyNames();
        
        return strategyNames.stream()
                .map(this::calculateMetrics)
                .collect(Collectors.toList());
    }
    
    /**
     * Retorna P&L total realizado de todas as estratégias
     */
    public BigDecimal getTotalRealizedPnL() {
        return tradeRepository.sumAllRealizedPnL().orElse(BigDecimal.ZERO);
    }
    
    /**
     * Retorna P&L total não realizado de todas as estratégias
     */
    public BigDecimal getTotalUnrealizedPnL() {
        return tradeRepository.sumAllUnrealizedPnL().orElse(BigDecimal.ZERO);
    }
    
    /**
     * Retorna P&L total (realizado + não realizado)
     */
    public BigDecimal getTotalPnL() {
        return getTotalRealizedPnL().add(getTotalUnrealizedPnL());
    }
    
    /**
     * Retorna a melhor estratégia por P&L
     */
    public String getBestStrategy() {
        return getAllStrategiesMetrics().stream()
                .filter(metrics -> metrics.getTotalPnL() != null)
                .max((m1, m2) -> m1.getTotalPnL().compareTo(m2.getTotalPnL()))
                .map(StrategyMetrics::getStrategyName)
                .orElse(null);
    }
    
    /**
     * Retorna a pior estratégia por P&L
     */
    public String getWorstStrategy() {
        return getAllStrategiesMetrics().stream()
                .filter(metrics -> metrics.getTotalPnL() != null)
                .min((m1, m2) -> m1.getTotalPnL().compareTo(m2.getTotalPnL()))
                .map(StrategyMetrics::getStrategyName)
                .orElse(null);
    }
    
    /**
     * Retorna as top N estratégias por performance
     */
    public List<StrategyMetrics> getTopPerformingStrategies(int limit) {
        return getAllStrategiesMetrics().stream()
                .filter(metrics -> metrics.getTotalPnL() != null)
                .sorted((m1, m2) -> m2.getTotalPnL().compareTo(m1.getTotalPnL()))
                .limit(limit)
                .collect(Collectors.toList());
    }
    
    /**
     * Atualiza P&L não realizado para trades abertos com preços atuais
     */
    public void updateUnrealizedPnL() {
        log.debug("Updating unrealized P&L for all open trades");
        
        List<Trade> openTrades = tradeRepository.findByStatus(TradeStatus.OPEN);
        
        for (Trade trade : openTrades) {
            try {
                BigDecimal unrealizedPnL = calculateTradeUnrealizedPnL(trade);
                trade.setUnrealizedPnL(unrealizedPnL);
                tradeRepository.save(trade);
                
            } catch (Exception e) {
                log.error("Error updating unrealized P&L for trade {}: {}", trade.getId(), e.getMessage());
            }
        }
        
        log.debug("Updated unrealized P&L for {} open trades", openTrades.size());
    }
}