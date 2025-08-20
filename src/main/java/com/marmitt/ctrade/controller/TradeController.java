package com.marmitt.ctrade.controller;

import com.marmitt.ctrade.application.service.TradeService;
import com.marmitt.ctrade.application.service.TradeService.TradePnLSummary;
import com.marmitt.ctrade.domain.entity.Trade;
import com.marmitt.ctrade.domain.valueobject.TradeStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
    
    private final TradeService tradeService;
    
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
            Page<Trade> trades = tradeService.getTrades(strategy, status, startDate, endDate, page, size);
            return ResponseEntity.ok(trades);
            
        } catch (Exception e) {
            log.error("TRADE_ERROR: Failed to get trades error={}", e.getMessage());
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
            Optional<Trade> trade = tradeService.getTradeById(id);
            
            if (trade.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            
            return ResponseEntity.ok(trade.get());
            
        } catch (Exception e) {
            log.error("TRADE_ERROR: Failed to get trade id={} error={}", id, e.getMessage());
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
            List<Trade> trades = tradeService.getTradesByStrategy(strategyName, limit);
            return ResponseEntity.ok(trades);
            
        } catch (Exception e) {
            log.error("TRADE_ERROR: Failed to get strategy trades strategy={} error={}", strategyName, e.getMessage());
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
            List<Trade> openTrades = tradeService.getOpenTradesByStrategy(strategyName);
            return ResponseEntity.ok(openTrades);
            
        } catch (Exception e) {
            log.error("TRADE_ERROR: Failed to get open trades strategy={} error={}", strategyName, e.getMessage());
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
            TradePnLSummary summary = tradeService.getStrategyPnLSummary(strategyName);
            return ResponseEntity.ok(summary);
            
        } catch (Exception e) {
            log.error("TRADE_ERROR: Failed to get PnL summary strategy={} error={}", strategyName, e.getMessage());
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
            List<Trade> recentTrades = tradeService.getRecentTrades(limit);
            return ResponseEntity.ok(recentTrades);
            
        } catch (Exception e) {
            log.error("TRADE_ERROR: Failed to get recent trades error={}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
    
}