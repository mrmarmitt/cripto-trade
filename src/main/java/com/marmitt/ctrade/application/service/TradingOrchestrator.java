package com.marmitt.ctrade.application.service;

import com.marmitt.ctrade.domain.entity.MarketData;
import com.marmitt.ctrade.domain.entity.Order;
import com.marmitt.ctrade.domain.entity.Portfolio;
import com.marmitt.ctrade.domain.entity.TradingPair;
import com.marmitt.ctrade.domain.port.ExchangePort;
import com.marmitt.ctrade.domain.port.TradingStrategy;
import com.marmitt.ctrade.domain.valueobject.StrategySignal;
import com.marmitt.ctrade.infrastructure.config.StrategyProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TradingOrchestrator {
    
    private final StrategyRegistry strategyRegistry;
    private final ExchangePort exchangePort;
    private final StrategyProperties strategyProperties;
    
    private final ExecutorService executorService = Executors.newFixedThreadPool(5);
    private Portfolio currentPortfolio = new Portfolio();
    
    @PostConstruct
    public void initialize() {
        log.info("TradingOrchestrator initialized with {} registered strategies", 
                strategyRegistry.getRegisteredCount());
    }
    
    public void executeStrategies(MarketData marketData) {
        if (marketData == null) {
            log.warn("Cannot execute strategies: MarketData is null");
            return;
        }
        
        List<TradingStrategy> activeStrategies = strategyRegistry.getActiveStrategies();
        
        if (activeStrategies.isEmpty()) {
            log.debug("No active strategies to execute");
            return;
        }
        
        log.debug("Executing {} active strategies with market data timestamp: {}", 
                activeStrategies.size(), marketData.getTimestamp());
        
        for (TradingStrategy strategy : activeStrategies) {
            CompletableFuture.runAsync(() -> executeStrategy(strategy, marketData), executorService)
                    .exceptionally(throwable -> {
                        log.error("Error executing strategy {}: {}", 
                                strategy.getStrategyName(), throwable.getMessage(), throwable);
                        return null;
                    });
        }
    }
    
    private void executeStrategy(TradingStrategy strategy, MarketData marketData) {
        try {
            log.debug("Analyzing strategy: {}", strategy.getStrategyName());
            
            StrategySignal signal = strategy.analyze(marketData, currentPortfolio);
            
            if (signal != null && signal.isActionable()) {
                log.info("Strategy {} generated signal: {} for pair {} - Reason: {}", 
                        strategy.getStrategyName(), signal.getType(), 
                        signal.getPair().getSymbol(), signal.getReason());
                
                processSignal(signal);
            } else {
                log.debug("Strategy {} generated HOLD signal", strategy.getStrategyName());
            }
            
        } catch (Exception e) {
            log.error("Error executing strategy {}: {}", strategy.getStrategyName(), e.getMessage(), e);
        }
    }
    
    private void processSignal(StrategySignal signal) {
        try {
            if (!validateSignal(signal)) {
                log.warn("Invalid signal from strategy {}: {}", signal.getStrategyName(), signal);
                return;
            }
            
            Order order = createOrderFromSignal(signal);
            
            log.info("Submitting order: {} {} {} at {} (Strategy: {})", 
                    order.getSide(), order.getQuantity(), order.getTradingPair().getSymbol(), 
                    order.getPrice(), signal.getStrategyName());
            
            exchangePort.placeOrder(order);
            
            updatePortfolioForOrder(order);
            
        } catch (Exception e) {
            log.error("Error processing signal from strategy {}: {}", 
                    signal.getStrategyName(), e.getMessage(), e);
        }
    }
    
    private boolean validateSignal(StrategySignal signal) {
        if (signal.getPair() == null) {
            log.warn("Signal validation failed: Trading pair is null");
            return false;
        }
        
        if (signal.getQuantity() == null || signal.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("Signal validation failed: Invalid quantity: {}", signal.getQuantity());
            return false;
        }
        
        if (signal.getPrice() == null || signal.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("Signal validation failed: Invalid price: {}", signal.getPrice());
            return false;
        }
        
        StrategyProperties.StrategyConfig config = strategyProperties.getStrategyConfig(signal.getStrategyName());
        BigDecimal orderValue = signal.getQuantity().multiply(signal.getPrice());
        
        if (orderValue.compareTo(config.getMaxOrderValue()) > 0) {
            log.warn("Signal validation failed: Order value {} exceeds maximum {}", 
                    orderValue, config.getMaxOrderValue());
            return false;
        }
        
        if (orderValue.compareTo(config.getMinOrderValue()) < 0) {
            log.warn("Signal validation failed: Order value {} below minimum {}", 
                    orderValue, config.getMinOrderValue());
            return false;
        }
        
        return true;
    }
    
    private Order createOrderFromSignal(StrategySignal signal) {
        Order.OrderSide orderSide = signal.getType() == com.marmitt.ctrade.domain.valueobject.SignalType.BUY ? 
                Order.OrderSide.BUY : Order.OrderSide.SELL;
        
        return new Order(
                signal.getPair(),
                Order.OrderType.LIMIT,
                orderSide,
                signal.getQuantity(),
                signal.getPrice()
        );
    }
    
    private void updatePortfolioForOrder(Order order) {
        try {
            TradingPair pair = order.getTradingPair();
            
            if (order.getSide() == Order.OrderSide.BUY) {
                BigDecimal totalCost = order.getTotalValue();
                currentPortfolio.subtractFromBalance(pair.getQuoteCurrency(), totalCost);
                currentPortfolio.addToBalance(pair.getBaseCurrency(), order.getQuantity());
            } else {
                BigDecimal totalValue = order.getTotalValue();
                currentPortfolio.subtractFromBalance(pair.getBaseCurrency(), order.getQuantity());
                currentPortfolio.addToBalance(pair.getQuoteCurrency(), totalValue);
            }
            
            log.debug("Updated portfolio for order: {} {} {}", 
                    order.getSide(), order.getQuantity(), order.getTradingPair().getSymbol());
            
        } catch (Exception e) {
            log.error("Error updating portfolio for order {}: {}", order.getId(), e.getMessage(), e);
        }
    }
    
    public void updatePortfolio(Portfolio portfolio) {
        this.currentPortfolio = portfolio;
        log.debug("Portfolio updated with {} holdings", portfolio.getHoldings().size());
    }
    
    public Portfolio getCurrentPortfolio() {
        return currentPortfolio;
    }
    
    public void enableStrategy(String strategyName) {
        strategyRegistry.enableStrategy(strategyName);
    }
    
    public void disableStrategy(String strategyName) {
        strategyRegistry.disableStrategy(strategyName);
    }
    
    public int getActiveStrategyCount() {
        return strategyRegistry.getActiveCount();
    }
    
    public List<TradingStrategy> getActiveStrategies() {
        return strategyRegistry.getActiveStrategies();
    }
}