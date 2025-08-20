package com.marmitt.ctrade.application.service;

import com.marmitt.ctrade.domain.entity.MarketData;
import com.marmitt.ctrade.domain.entity.Order;
import com.marmitt.ctrade.domain.entity.Portfolio;
import com.marmitt.ctrade.domain.entity.Trade;
import com.marmitt.ctrade.domain.entity.TradingPair;
import com.marmitt.ctrade.domain.port.ExchangePort;
import com.marmitt.ctrade.domain.port.TradingStrategy;
import com.marmitt.ctrade.domain.valueobject.SignalType;
import com.marmitt.ctrade.domain.valueobject.StrategySignal;
import com.marmitt.ctrade.domain.valueobject.TradeType;
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
    private final TradeMatchingService tradeMatchingService;
    private final StrategyPerformanceTracker performanceTracker;
    
    private final ExecutorService executorService = Executors.newFixedThreadPool(5);
    private Portfolio currentPortfolio = new Portfolio();
    
    @PostConstruct
    public void initialize() {
        log.info("TradingOrchestrator initialized with {} registered strategies", 
                strategyRegistry.getRegisteredCount());
        
        // Initialize portfolio with demo balances for development
        initializeDemoPortfolio();
    }
    
    /**
     * Initializes portfolio with demo balances for development and testing
     */
    private void initializeDemoPortfolio() {
        try {
            // Add demo balances
            currentPortfolio.updateBalance("USDT", new BigDecimal("10000.00")); // 10,000 USDT
            currentPortfolio.updateBalance("BTC", new BigDecimal("0.1"));       // 0.1 BTC
            currentPortfolio.updateBalance("ETH", new BigDecimal("2.0"));       // 2.0 ETH
            currentPortfolio.updateBalance("BNB", new BigDecimal("50.0"));      // 50 BNB
            
            log.info("Demo portfolio initialized with balances: USDT=10000, BTC=0.1, ETH=2.0, BNB=50");
            
        } catch (Exception e) {
            log.error("Error initializing demo portfolio: {}", e.getMessage(), e);
        }
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
                log.info("ORDER_VALIDATION: strategy={} signal={} pair={} price={} quantity={} reason={}", 
                        strategy.getStrategyName(), signal.getType(), 
                        signal.getPair().getSymbol(), signal.getPrice(), signal.getQuantity(), signal.getReason());
                
                processSignal(signal);
            } else {
                String holdReason = (signal != null) ? signal.getReason() : "No signal generated";
                log.info("ORDER_VALIDATION: strategy={} signal=HOLD reason={}", strategy.getStrategyName(), holdReason);
            }
            
        } catch (Exception e) {
            log.error("Error executing strategy {}: {}", strategy.getStrategyName(), e.getMessage(), e);
        }
    }
    
    private void processSignal(StrategySignal signal) {
        try {
            if (!validateSignal(signal)) {
                log.warn("ORDER_VALIDATION: Invalid signal strategy={} type={}", signal.getStrategyName(), signal.getType());
                return;
            }
            
            Order order = createOrderFromSignal(signal);
            
            log.info("ORDER_VALIDATION: Submitting order strategy={} side={} pair={} quantity={} price={}", 
                    signal.getStrategyName(), order.getSide(), order.getTradingPair().getSymbol(), 
                    order.getQuantity(), order.getPrice());
            
            // Execute order through exchange
            exchangePort.placeOrder(order);
            
            // Track trade for P&L calculation
            trackTradeExecution(signal, order);
            
            // Update portfolio
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
        
        // Validate portfolio balance
        if (!validatePortfolioBalance(signal)) {
            return false;
        }
        
        return true;
    }
    
    /**
     * Validates if portfolio has sufficient balance for the signal
     */
    private boolean validatePortfolioBalance(StrategySignal signal) {
        try {
            String requiredCurrency;
            BigDecimal requiredAmount;
            
            if (signal.getType() == SignalType.BUY) {
                // For BUY signals, need quote currency (e.g., USDT)
                requiredCurrency = signal.getPair().getQuoteCurrency();
                requiredAmount = signal.getQuantity().multiply(signal.getPrice());
            } else {
                // For SELL signals, need base currency (e.g., BTC)
                requiredCurrency = signal.getPair().getBaseCurrency();
                requiredAmount = signal.getQuantity();
            }
            
            BigDecimal currentBalance = currentPortfolio.getBalance(requiredCurrency);
            
            if (currentBalance.compareTo(requiredAmount) < 0) {
                log.warn("Signal validation failed: Insufficient {} balance. Required: {}, Available: {}", 
                        requiredCurrency, requiredAmount, currentBalance);
                
                log.debug("Current portfolio balances: {}", currentPortfolio.getHoldings());
                return false;
            }
            
            log.debug("Portfolio balance validation passed: {} {} available (need {})", 
                    currentBalance, requiredCurrency, requiredAmount);
            
            return true;
            
        } catch (Exception e) {
            log.error("Error validating portfolio balance: {}", e.getMessage(), e);
            return false;
        }
    }
    
    private Order createOrderFromSignal(StrategySignal signal) {
        Order.OrderSide orderSide = signal.getType() == SignalType.BUY ? 
                Order.OrderSide.BUY : Order.OrderSide.SELL;
        
        return new Order(
                signal.getPair(),
                Order.OrderType.LIMIT,
                orderSide,
                signal.getQuantity(),
                signal.getPrice()
        );
    }
    
    /**
     * Tracks trade execution for P&L calculation
     */
    private void trackTradeExecution(StrategySignal signal, Order order) {
        try {
            String strategyName = signal.getStrategyName();
            TradingPair tradingPair = signal.getPair();
            BigDecimal price = signal.getPrice();
            BigDecimal quantity = signal.getQuantity();
            String orderId = order.getId() != null ? order.getId() : generateOrderId();
            
            if (signal.getType() == SignalType.BUY) {
                // Opening a position (or adding to existing position)
                TradeType tradeType = TradeType.LONG; // Assuming buy signals are LONG positions
                
                Trade trade = tradeMatchingService.openTrade(
                        strategyName, tradingPair, tradeType, price, quantity, orderId);
                
                log.info("ORDER_VALIDATION: Opened trade strategy={} tradeId={} type={} pair={} quantity={} price={}", 
                        strategyName, trade.getId(), tradeType, tradingPair.getSymbol(), quantity, price);
                
            } else if (signal.getType() == SignalType.SELL) {
                // Closing position(s)
                if (tradeMatchingService.hasOpenTrades(strategyName, tradingPair)) {
                    TradeMatchingService.MatchingResult result = tradeMatchingService.closeTrade(
                            strategyName, tradingPair, price, quantity, orderId);
                    
                    log.info("ORDER_VALIDATION: Closed trades strategy={} pair={} matchedQuantity={} realizedPnL={}", 
                            strategyName, tradingPair.getSymbol(), result.getTotalMatchedQuantity(), result.getTotalRealizedPnL());
                    
                    if (result.isPartialMatch()) {
                        log.warn("ORDER_VALIDATION: Partial match strategy={} remainingQuantity={}", 
                                strategyName, result.getRemainingQuantity());
                    }
                } else {
                    log.warn("ORDER_VALIDATION: Sell signal no open trades strategy={} pair={}", 
                            strategyName, tradingPair.getSymbol());
                }
            }
            
        } catch (Exception e) {
            log.error("Error tracking trade execution for strategy {}: {}", 
                    signal.getStrategyName(), e.getMessage(), e);
        }
    }
    
    /**
     * Generates a unique order ID if not provided by the Order
     */
    private String generateOrderId() {
        return "ORDER_" + System.currentTimeMillis() + "_" + System.nanoTime() % 1000;
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
    
    /**
     * Updates unrealized P&L for all open trades
     */
    public void updateUnrealizedPnL() {
        try {
            log.debug("Updating unrealized P&L for all open trades");
            performanceTracker.updateUnrealizedPnL();
        } catch (Exception e) {
            log.error("Error updating unrealized P&L: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Gets strategy performance metrics
     */
    public com.marmitt.ctrade.domain.valueobject.StrategyMetrics getStrategyMetrics(String strategyName) {
        try {
            return performanceTracker.calculateMetrics(strategyName);
        } catch (Exception e) {
            log.error("Error calculating metrics for strategy {}: {}", strategyName, e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Gets total P&L across all strategies
     */
    public BigDecimal getTotalPnL() {
        try {
            return performanceTracker.getTotalPnL();
        } catch (Exception e) {
            log.error("Error calculating total P&L: {}", e.getMessage(), e);
            return BigDecimal.ZERO;
        }
    }
    
    /**
     * Forces closure of all open trades for a strategy
     */
    public void forceCloseAllTrades(String strategyName, TradingPair tradingPair, BigDecimal exitPrice) {
        try {
            TradeMatchingService.MatchingResult result = tradeMatchingService.forceCloseAllTrades(
                    strategyName, tradingPair, exitPrice);
            
            log.info("Force closed all trades for strategy '{}': matched {} {}, realized P&L: {}", 
                    strategyName, result.getTotalMatchedQuantity(), 
                    tradingPair.getSymbol(), result.getTotalRealizedPnL());
            
        } catch (Exception e) {
            log.error("Error force closing trades for strategy {}: {}", strategyName, e.getMessage(), e);
        }
    }
    
    /**
     * Checks if a strategy has open trades
     */
    public boolean hasOpenTrades(String strategyName, TradingPair tradingPair) {
        return tradeMatchingService.hasOpenTrades(strategyName, tradingPair);
    }
    
    /**
     * Gets total open quantity for a strategy and trading pair
     */
    public BigDecimal getTotalOpenQuantity(String strategyName, TradingPair tradingPair) {
        return tradeMatchingService.getTotalOpenQuantity(strategyName, tradingPair);
    }
}