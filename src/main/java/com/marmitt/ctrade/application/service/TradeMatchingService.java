package com.marmitt.ctrade.application.service;

import com.marmitt.ctrade.domain.entity.Trade;
import com.marmitt.ctrade.domain.entity.TradingPair;
import com.marmitt.ctrade.domain.valueobject.TradeStatus;
import com.marmitt.ctrade.domain.valueobject.TradeType;
import com.marmitt.ctrade.infrastructure.repository.TradeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Serviço responsável por fazer matching de trades usando estratégia FIFO (First In, First Out).
 * Pareia ordens de entrada e saída para calcular P&L realizado.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TradeMatchingService {
    
    private final TradeRepository tradeRepository;
    
    /**
     * Registra uma entrada de trade (abertura de posição)
     */
    @Transactional
    public Trade openTrade(String strategyName, TradingPair tradingPair, TradeType tradeType,
                          BigDecimal entryPrice, BigDecimal quantity, String entryOrderId) {
        
        log.debug("Opening trade for strategy '{}': {} {} {} at {}", 
                strategyName, tradeType, quantity, tradingPair.getSymbol(), entryPrice);
        
        // Verifica se já existe trade com este orderId
        if (tradeRepository.existsByEntryOrderId(entryOrderId)) {
            log.warn("Trade with entry order ID {} already exists", entryOrderId);
            throw new IllegalArgumentException("Trade with entry order ID already exists: " + entryOrderId);
        }
        
        Trade trade = new Trade();
        trade.setStrategyName(strategyName);
        trade.setPair(tradingPair);
        trade.setType(tradeType);
        trade.setStatus(TradeStatus.OPEN);
        trade.setEntryPrice(entryPrice);
        trade.setEntryQuantity(quantity);
        trade.setRemainingQuantity(quantity);
        trade.setEntryTime(LocalDateTime.now());
        trade.setEntryOrderId(entryOrderId);
        trade.setInvestedAmount(entryPrice.multiply(quantity));
        trade.setRealizedPnL(BigDecimal.ZERO);
        trade.setUnrealizedPnL(BigDecimal.ZERO);
        
        Trade savedTrade = tradeRepository.save(trade);
        
        log.info("Opened trade {} for strategy '{}': {} {} {} at {} (invested: {})", 
                savedTrade.getId(), strategyName, tradeType, quantity, 
                tradingPair.getSymbol(), entryPrice, trade.getInvestedAmount());
        
        return savedTrade;
    }
    
    /**
     * Registra uma saída de trade usando matching FIFO
     */
    @Transactional
    public MatchingResult closeTrade(String strategyName, TradingPair tradingPair,
                                   BigDecimal exitPrice, BigDecimal quantity, String exitOrderId) {
        
        log.debug("Closing trade for strategy '{}': {} {} at {}", 
                strategyName, quantity, tradingPair.getSymbol(), exitPrice);
        
        // Verifica se já existe trade com este orderId
        if (exitOrderId != null && tradeRepository.findByExitOrderId(exitOrderId).isPresent()) {
            log.warn("Trade with exit order ID {} already exists", exitOrderId);
            throw new IllegalArgumentException("Trade with exit order ID already exists: " + exitOrderId);
        }
        
        BigDecimal remainingQuantity = quantity;
        BigDecimal totalRealizedPnL = BigDecimal.ZERO;
        MatchingResult result = new MatchingResult();
        
        // Busca trades abertos mais antigos (FIFO)
        List<Trade> openTrades = tradeRepository.findOldestOpenTrades(
                strategyName, tradingPair, TradeStatus.OPEN);
        
        if (openTrades.isEmpty()) {
            log.warn("No open trades found for strategy '{}' and pair '{}'", strategyName, tradingPair.getSymbol());
            throw new IllegalStateException("No open trades available for closing");
        }
        
        for (Trade openTrade : openTrades) {
            if (remainingQuantity.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
            
            BigDecimal matchedQuantity = remainingQuantity.min(openTrade.getRemainingQuantity());
            BigDecimal matchedPnL = calculateRealizedPnL(openTrade, exitPrice, matchedQuantity);
            
            // Atualiza o trade aberto
            updateTradeForMatching(openTrade, exitPrice, matchedQuantity, exitOrderId, matchedPnL);
            
            totalRealizedPnL = totalRealizedPnL.add(matchedPnL);
            remainingQuantity = remainingQuantity.subtract(matchedQuantity);
            
            result.addMatchedTrade(openTrade, matchedQuantity, matchedPnL);
            
            log.debug("Matched {} with trade {} - quantity: {}, P&L: {}", 
                    exitOrderId, openTrade.getId(), matchedQuantity, matchedPnL);
        }
        
        if (remainingQuantity.compareTo(BigDecimal.ZERO) > 0) {
            log.warn("Partial matching only - remaining quantity: {}", remainingQuantity);
            result.setPartialMatch(true);
            result.setRemainingQuantity(remainingQuantity);
        }
        
        result.setTotalRealizedPnL(totalRealizedPnL);
        result.setTotalMatchedQuantity(quantity.subtract(remainingQuantity));
        
        log.info("Closed trades for strategy '{}': matched {} {}, realized P&L: {}", 
                strategyName, result.getTotalMatchedQuantity(), tradingPair.getSymbol(), totalRealizedPnL);
        
        return result;
    }
    
    /**
     * Calcula P&L realizado para uma quantidade específica
     */
    private BigDecimal calculateRealizedPnL(Trade openTrade, BigDecimal exitPrice, BigDecimal quantity) {
        BigDecimal entryValue = openTrade.getEntryPrice().multiply(quantity);
        BigDecimal exitValue = exitPrice.multiply(quantity);
        
        // Para trades LONG: P&L = (preço_saída - preço_entrada) * quantidade
        // Para trades SHORT: P&L = (preço_entrada - preço_saída) * quantidade
        BigDecimal pnl;
        if (openTrade.getType() == TradeType.LONG) {
            pnl = exitValue.subtract(entryValue);
        } else {
            pnl = entryValue.subtract(exitValue);
        }
        
        return pnl.setScale(8, RoundingMode.HALF_UP);
    }
    
    /**
     * Atualiza trade após matching
     */
    private void updateTradeForMatching(Trade trade, BigDecimal exitPrice, BigDecimal matchedQuantity, 
                                      String exitOrderId, BigDecimal matchedPnL) {
        
        BigDecimal newRemainingQuantity = trade.getRemainingQuantity().subtract(matchedQuantity);
        trade.setRemainingQuantity(newRemainingQuantity);
        
        // Acumula P&L realizado
        BigDecimal currentRealizedPnL = trade.getRealizedPnL() != null ? trade.getRealizedPnL() : BigDecimal.ZERO;
        trade.setRealizedPnL(currentRealizedPnL.add(matchedPnL));
        
        // Se completamente fechado
        if (newRemainingQuantity.compareTo(BigDecimal.ZERO) == 0) {
            trade.setStatus(TradeStatus.CLOSED);
            trade.setExitPrice(exitPrice);
            trade.setExitTime(LocalDateTime.now());
            trade.setExitOrderId(exitOrderId);
            trade.setUnrealizedPnL(BigDecimal.ZERO); // Não há mais P&L não realizado
            
            // Calcula período de holding
            if (trade.getEntryTime() != null && trade.getExitTime() != null) {
                Duration holdingPeriod = Duration.between(trade.getEntryTime(), trade.getExitTime());
                trade.setHoldingPeriodSeconds(holdingPeriod.getSeconds());
            }
            
        } else {
            // Parcialmente fechado
            trade.setStatus(TradeStatus.PARTIAL_CLOSED);
            // Calcula preço médio de saída
            if (trade.getExitPrice() == null) {
                trade.setExitPrice(exitPrice);
            } else {
                // Média ponderada dos preços de saída
                BigDecimal totalExitedQuantity = trade.getEntryQuantity().subtract(newRemainingQuantity);
                BigDecimal weightedExitPrice = trade.getExitPrice()
                        .multiply(totalExitedQuantity.subtract(matchedQuantity))
                        .add(exitPrice.multiply(matchedQuantity))
                        .divide(totalExitedQuantity, 8, RoundingMode.HALF_UP);
                trade.setExitPrice(weightedExitPrice);
            }
            
            if (trade.getExitTime() == null) {
                trade.setExitTime(LocalDateTime.now());
            }
            
            if (trade.getExitOrderId() == null) {
                trade.setExitOrderId(exitOrderId);
            }
        }
        
        tradeRepository.save(trade);
    }
    
    /**
     * Busca o primeiro trade aberto para uma estratégia e par
     */
    public Optional<Trade> getFirstOpenTrade(String strategyName, TradingPair tradingPair) {
        return tradeRepository.findFirstOpenTrade(strategyName, tradingPair);
    }
    
    /**
     * Verifica se há trades abertos para uma estratégia
     */
    public boolean hasOpenTrades(String strategyName, TradingPair tradingPair) {
        return getFirstOpenTrade(strategyName, tradingPair).isPresent();
    }
    
    /**
     * Retorna quantidade total em posições abertas
     */
    public BigDecimal getTotalOpenQuantity(String strategyName, TradingPair tradingPair) {
        List<Trade> openTrades = tradeRepository.findByStrategyNameAndPair(strategyName, tradingPair)
                .stream()
                .filter(trade -> trade.getStatus() == TradeStatus.OPEN || trade.getStatus() == TradeStatus.PARTIAL_CLOSED)
                .toList();
        
        return openTrades.stream()
                .map(Trade::getRemainingQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    
    /**
     * Força fechamento de todas as posições abertas de uma estratégia
     */
    @Transactional
    public MatchingResult forceCloseAllTrades(String strategyName, TradingPair tradingPair, BigDecimal exitPrice) {
        log.info("Force closing all trades for strategy '{}' and pair '{}'", strategyName, tradingPair.getSymbol());
        
        BigDecimal totalOpenQuantity = getTotalOpenQuantity(strategyName, tradingPair);
        
        if (totalOpenQuantity.compareTo(BigDecimal.ZERO) == 0) {
            log.info("No open trades to close for strategy '{}'", strategyName);
            return new MatchingResult();
        }
        
        return closeTrade(strategyName, tradingPair, exitPrice, totalOpenQuantity, "FORCE_CLOSE_" + System.currentTimeMillis());
    }
    
    /**
     * Resultado de uma operação de matching
     */
    public static class MatchingResult {
        private BigDecimal totalRealizedPnL = BigDecimal.ZERO;
        private BigDecimal totalMatchedQuantity = BigDecimal.ZERO;
        private BigDecimal remainingQuantity = BigDecimal.ZERO;
        private boolean partialMatch = false;
        private final List<MatchedTrade> matchedTrades = new java.util.ArrayList<>();
        
        public void addMatchedTrade(Trade trade, BigDecimal matchedQuantity, BigDecimal realizedPnL) {
            matchedTrades.add(new MatchedTrade(trade.getId(), matchedQuantity, realizedPnL));
        }
        
        // Getters and setters
        public BigDecimal getTotalRealizedPnL() { return totalRealizedPnL; }
        public void setTotalRealizedPnL(BigDecimal totalRealizedPnL) { this.totalRealizedPnL = totalRealizedPnL; }
        
        public BigDecimal getTotalMatchedQuantity() { return totalMatchedQuantity; }
        public void setTotalMatchedQuantity(BigDecimal totalMatchedQuantity) { this.totalMatchedQuantity = totalMatchedQuantity; }
        
        public BigDecimal getRemainingQuantity() { return remainingQuantity; }
        public void setRemainingQuantity(BigDecimal remainingQuantity) { this.remainingQuantity = remainingQuantity; }
        
        public boolean isPartialMatch() { return partialMatch; }
        public void setPartialMatch(boolean partialMatch) { this.partialMatch = partialMatch; }
        
        public List<MatchedTrade> getMatchedTrades() { return List.copyOf(matchedTrades); }
        
        public static class MatchedTrade {
            private final Long tradeId;
            private final BigDecimal matchedQuantity;
            private final BigDecimal realizedPnL;
            
            public MatchedTrade(Long tradeId, BigDecimal matchedQuantity, BigDecimal realizedPnL) {
                this.tradeId = tradeId;
                this.matchedQuantity = matchedQuantity;
                this.realizedPnL = realizedPnL;
            }
            
            public Long getTradeId() { return tradeId; }
            public BigDecimal getMatchedQuantity() { return matchedQuantity; }
            public BigDecimal getRealizedPnL() { return realizedPnL; }
        }
    }
}