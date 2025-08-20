package com.marmitt.ctrade.application.service;

import com.marmitt.ctrade.domain.entity.Trade;
import com.marmitt.ctrade.domain.valueobject.TradeStatus;
import com.marmitt.ctrade.infrastructure.repository.TradeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TradeService {

    private final TradeRepository tradeRepository;

    public Page<Trade> getTrades(String strategy, TradeStatus status, LocalDateTime startDate, 
                                LocalDateTime endDate, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Trade> trades = tradeRepository.findTrades(strategy, status, startDate, endDate, pageable);
        
        return trades;
    }

    public Optional<Trade> getTradeById(Long id) {
        Optional<Trade> trade = tradeRepository.findById(id);
        
        if (trade.isEmpty()) {
            log.warn("TRADE_VALIDATION: Trade not found id={}", id);
        }
        
        return trade;
    }

    public List<Trade> getTradesByStrategy(String strategyName, int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        List<Trade> trades = tradeRepository.findRecentTradesByStrategy(strategyName, pageable);
        
        return trades;
    }

    public List<Trade> getOpenTradesByStrategy(String strategyName) {
        List<Trade> openTrades = tradeRepository.findByStrategyNameAndStatus(strategyName, TradeStatus.OPEN);
        
        log.info("TRADE_VALIDATION: strategy={} openTrades={}", strategyName, openTrades.size());
        
        return openTrades;
    }

    public TradePnLSummary getStrategyPnLSummary(String strategyName) {
        Optional<BigDecimal> totalRealized = tradeRepository.sumRealizedPnLByStrategy(strategyName);
        Optional<BigDecimal> totalUnrealized = tradeRepository.sumUnrealizedPnLByStrategy(strategyName);
        Optional<BigDecimal> totalPnL = tradeRepository.sumTotalPnLByStrategy(strategyName);
        
        Long totalTrades = tradeRepository.countByStrategyName(strategyName);
        Long openTrades = tradeRepository.countByStrategyNameAndStatus(strategyName, TradeStatus.OPEN);
        Long closedTrades = tradeRepository.countByStrategyNameAndStatus(strategyName, TradeStatus.CLOSED);
        
        Long winningTrades = tradeRepository.countWinningTrades(strategyName);
        Long losingTrades = tradeRepository.countLosingTrades(strategyName);
        
        BigDecimal realizedPnL = totalRealized.orElse(BigDecimal.ZERO);
        BigDecimal unrealizedPnL = totalUnrealized.orElse(BigDecimal.ZERO);
        BigDecimal finalTotalPnL = totalPnL.orElse(BigDecimal.ZERO);
        
        TradePnLSummary summary = new TradePnLSummary(
                strategyName,
                realizedPnL,
                unrealizedPnL,
                finalTotalPnL,
                totalTrades,
                openTrades,
                closedTrades,
                winningTrades,
                losingTrades
        );
        
        log.info("TRADE_VALIDATION: strategy={} realizedPnL={} unrealizedPnL={} totalPnL={} total={} open={} closed={} win={} loss={}", 
                strategyName, realizedPnL, unrealizedPnL, finalTotalPnL, totalTrades, openTrades, closedTrades, winningTrades, losingTrades);
        
        return summary;
    }

    public List<Trade> getRecentTrades(int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        List<Trade> recentTrades = tradeRepository.findRecentTrades(pageable);
        
        return recentTrades;
    }

    public static record TradePnLSummary(
            String strategyName,
            BigDecimal totalRealizedPnL,
            BigDecimal totalUnrealizedPnL,
            BigDecimal totalPnL,
            Long totalTrades,
            Long openTrades,
            Long closedTrades,
            Long winningTrades,
            Long losingTrades
    ) {}
}