package com.marmitt.ctrade.controller;

import com.marmitt.ctrade.domain.entity.Trade;
import com.marmitt.ctrade.domain.valueobject.TradeStatus;
import com.marmitt.ctrade.infrastructure.repository.TradeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Controller REST para consulta de trades individuais
 */
@Slf4j
@RestController
@RequestMapping("/api/trades")
@RequiredArgsConstructor
public class TradeController {
    
    private final TradeRepository tradeRepository;
    
    /**
     * GET /api/trades?strategy=X&status=Y&page=0&size=10
     * Lista trades com filtros opcionais
     */
    @GetMapping
    public ResponseEntity<Page<Trade>> getTrades(
            @RequestParam(required = false) String strategy,
            @RequestParam(required = false) TradeStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        try {
            log.info("Requested trades: strategy={}, status={}, page={}, size={}", 
                    strategy, status, page, size);
            
            Pageable pageable = PageRequest.of(page, size);
            Page<Trade> trades = tradeRepository.findTrades(strategy, status, startDate, endDate, pageable);
            
            log.debug("Returning {} trades (page {} of {})", 
                    trades.getNumberOfElements(), trades.getNumber() + 1, trades.getTotalPages());
            
            return ResponseEntity.ok(trades);
            
        } catch (Exception e) {
            log.error("Error retrieving trades: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * GET /api/trades/{id}
     * Retorna um trade específico por ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<Trade> getTrade(@PathVariable Long id) {
        try {
            log.info("Requested trade with ID: {}", id);
            
            Optional<Trade> trade = tradeRepository.findById(id);
            
            if (trade.isEmpty()) {
                log.warn("Trade not found with ID: {}", id);
                return ResponseEntity.notFound().build();
            }
            
            return ResponseEntity.ok(trade.get());
            
        } catch (Exception e) {
            log.error("Error retrieving trade {}: {}", id, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * GET /api/trades/strategy/{strategyName}
     * Lista todos os trades de uma estratégia específica
     */
    @GetMapping("/strategy/{strategyName}")
    public ResponseEntity<List<Trade>> getTradesByStrategy(
            @PathVariable String strategyName,
            @RequestParam(defaultValue = "50") int limit) {
        
        try {
            log.info("Requested trades for strategy: {} (limit: {})", strategyName, limit);
            
            Pageable pageable = PageRequest.of(0, limit);
            List<Trade> trades = tradeRepository.findRecentTradesByStrategy(strategyName, pageable);
            
            log.debug("Returning {} trades for strategy: {}", trades.size(), strategyName);
            
            return ResponseEntity.ok(trades);
            
        } catch (Exception e) {
            log.error("Error retrieving trades for strategy {}: {}", strategyName, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * GET /api/trades/strategy/{strategyName}/open
     * Lista trades abertos de uma estratégia
     */
    @GetMapping("/strategy/{strategyName}/open")
    public ResponseEntity<List<Trade>> getOpenTradesByStrategy(@PathVariable String strategyName) {
        try {
            log.info("Requested open trades for strategy: {}", strategyName);
            
            List<Trade> openTrades = tradeRepository.findByStrategyNameAndStatus(strategyName, TradeStatus.OPEN);
            
            log.debug("Returning {} open trades for strategy: {}", openTrades.size(), strategyName);
            
            return ResponseEntity.ok(openTrades);
            
        } catch (Exception e) {
            log.error("Error retrieving open trades for strategy {}: {}", strategyName, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * GET /api/trades/strategy/{strategyName}/pnl
     * Retorna resumo de P&L para uma estratégia
     */
    @GetMapping("/strategy/{strategyName}/pnl")
    public ResponseEntity<TradePnLSummary> getStrategyPnLSummary(@PathVariable String strategyName) {
        try {
            log.info("Requested P&L summary for strategy: {}", strategyName);
            
            Optional<BigDecimal> totalRealized = tradeRepository.sumRealizedPnLByStrategy(strategyName);
            Optional<BigDecimal> totalUnrealized = tradeRepository.sumUnrealizedPnLByStrategy(strategyName);
            Optional<BigDecimal> totalPnL = tradeRepository.sumTotalPnLByStrategy(strategyName);
            
            Long totalTrades = tradeRepository.countByStrategyName(strategyName);
            Long openTrades = tradeRepository.countByStrategyNameAndStatus(strategyName, TradeStatus.OPEN);
            Long closedTrades = tradeRepository.countByStrategyNameAndStatus(strategyName, TradeStatus.CLOSED);
            
            Long winningTrades = tradeRepository.countWinningTrades(strategyName);
            Long losingTrades = tradeRepository.countLosingTrades(strategyName);
            
            TradePnLSummary summary = new TradePnLSummary(
                    strategyName,
                    totalRealized.orElse(BigDecimal.ZERO),
                    totalUnrealized.orElse(BigDecimal.ZERO),
                    totalPnL.orElse(BigDecimal.ZERO),
                    totalTrades,
                    openTrades,
                    closedTrades,
                    winningTrades,
                    losingTrades
            );
            
            log.debug("P&L summary for {}: Total={}, Open={}, Closed={}", 
                    strategyName, totalPnL.orElse(BigDecimal.ZERO), openTrades, closedTrades);
            
            return ResponseEntity.ok(summary);
            
        } catch (Exception e) {
            log.error("Error retrieving P&L summary for strategy {}: {}", strategyName, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * GET /api/trades/recent
     * Lista trades recentes (todas as estratégias)
     */
    @GetMapping("/recent")
    public ResponseEntity<List<Trade>> getRecentTrades(@RequestParam(defaultValue = "20") int limit) {
        try {
            log.info("Requested recent trades (limit: {})", limit);
            
            Pageable pageable = PageRequest.of(0, limit);
            List<Trade> recentTrades = tradeRepository.findRecentTrades(pageable);
            
            log.debug("Returning {} recent trades", recentTrades.size());
            
            return ResponseEntity.ok(recentTrades);
            
        } catch (Exception e) {
            log.error("Error retrieving recent trades: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * DTO para resumo de P&L de trades
     */
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