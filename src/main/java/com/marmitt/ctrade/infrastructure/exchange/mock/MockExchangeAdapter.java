package com.marmitt.ctrade.infrastructure.exchange.mock;

import com.marmitt.ctrade.domain.entity.Order;
import com.marmitt.ctrade.domain.entity.TradingPair;
import com.marmitt.ctrade.domain.port.ExchangePort;
import com.marmitt.ctrade.domain.valueobject.Price;
import com.marmitt.ctrade.infrastructure.config.MockExchangeProperties;
import com.marmitt.ctrade.infrastructure.exchange.mock.dto.PriceData;
import com.marmitt.ctrade.infrastructure.exchange.mock.service.MockMarketDataLoader;
import com.marmitt.ctrade.infrastructure.exchange.mock.service.OrderEventListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@RequiredArgsConstructor
public class MockExchangeAdapter implements ExchangePort {

    private final MockExchangeProperties mockProperties;
    private final MockMarketDataLoader marketDataLoader;
    private final Map<String, Order> orders = new ConcurrentHashMap<>();
    private final Map<TradingPair, Price> prices = new ConcurrentHashMap<>();
    
    // Lista de listeners para eventos de ordem
    private final List<OrderEventListener> orderEventListeners = new ArrayList<>();
    
    // Processamento periódico de ordens pendentes
    private final ScheduledExecutorService orderProcessor = Executors.newSingleThreadScheduledExecutor();
    
    @PostConstruct
    public void initialize() {
        initializePrices();
        startOrderProcessor();
    }

    @Override
    public Order placeOrder(Order order) {
        log.info("📋 Placing order: {} {} {} {} at {} (ID: {})", 
            order.getSide(), order.getQuantity(), order.getTradingPair().getSymbol(), 
            order.getType(), order.getPrice(), order.getId());
        
        // Check order acceptance rate
        double acceptanceChance = ThreadLocalRandom.current().nextDouble();
        if (acceptanceChance > mockProperties.getOrders().getAcceptanceRate()) {
            order.updateStatus(Order.OrderStatus.CANCELLED);
            log.warn("❌ Order rejected: {} (acceptance chance: {:.2f}, rate: {})", 
                order.getId(), acceptanceChance, mockProperties.getOrders().getAcceptanceRate());
            orders.put(order.getId(), order);
            notifyOrderUpdate(order);
            return order;
        }
        
        log.debug("✅ Order accepted: {} (chance: {:.2f}, rate: {})", 
            order.getId(), acceptanceChance, mockProperties.getOrders().getAcceptanceRate());
        
        // Process order based on type
        if (order.getType() == Order.OrderType.MARKET) {
            processMarketOrder(order);
        } else {
            processLimitOrder(order);
        }
        
        orders.put(order.getId(), order);
        
        // Notifica listeners sobre a mudança de status da ordem
        notifyOrderUpdate(order);
        
        return order;
    }
    
    private boolean shouldAcceptOrder() {
        return ThreadLocalRandom.current().nextDouble() < mockProperties.getOrders().getAcceptanceRate();
    }
    
    private void processMarketOrder(Order order) {
        // Market orders are filled immediately with slippage
        BigDecimal currentPrice = getCurrentMarketPrice(order.getTradingPair());
        BigDecimal slippage = calculateSlippage(currentPrice);
        BigDecimal executionPrice = order.getSide() == Order.OrderSide.BUY ? 
            currentPrice.add(slippage) : currentPrice.subtract(slippage);
        
        order.setPrice(executionPrice);
        order.updateStatus(Order.OrderStatus.FILLED);
        
        log.info("Market order filled: {} at {} (slippage: {})", 
            order.getId(), executionPrice, slippage);
    }
    
    private void processLimitOrder(Order order) {
        BigDecimal currentPrice = getCurrentMarketPrice(order.getTradingPair());
        boolean shouldExecute = shouldExecuteLimitOrder(order, currentPrice);
        
        log.debug("Processing limit order {}: {} {} {} at {} (current price: {}, should execute: {})", 
            order.getId(), order.getSide(), order.getQuantity(), order.getTradingPair().getSymbol(), 
            order.getPrice(), currentPrice, shouldExecute);
        
        if (shouldExecute) {
            // Check for partial fill
            if (ThreadLocalRandom.current().nextDouble() < mockProperties.getOrders().getPartialFillRate()) {
                order.updateStatus(Order.OrderStatus.PARTIALLY_FILLED);
                log.info("✅ Limit order partially filled: {} {} {} at {} (current: {})", 
                    order.getId(), order.getSide(), order.getTradingPair().getSymbol(), order.getPrice(), currentPrice);
            } else {
                order.updateStatus(Order.OrderStatus.FILLED);
                log.info("✅ Limit order filled: {} {} {} at {} (current: {})", 
                    order.getId(), order.getSide(), order.getTradingPair().getSymbol(), order.getPrice(), currentPrice);
            }
        } else {
            // Check if order should remain open or be executed anyway
            double executionChance = ThreadLocalRandom.current().nextDouble();
            if (executionChance < mockProperties.getOrders().getExecutionRate()) {
                order.updateStatus(Order.OrderStatus.FILLED);
                log.info("✅ Limit order executed (forced): {} {} {} at {} (current: {}, chance: {:.2f})", 
                    order.getId(), order.getSide(), order.getTradingPair().getSymbol(), order.getPrice(), 
                    currentPrice, executionChance);
            } else {
                order.updateStatus(Order.OrderStatus.PENDING);
                log.warn("⏳ Limit order pending: {} {} {} at {} (current price: {}, margin check failed, chance: {:.2f})", 
                    order.getId(), order.getSide(), order.getTradingPair().getSymbol(), order.getPrice(), 
                    currentPrice, executionChance);
            }
        }
    }
    
    private boolean shouldExecuteLimitOrder(Order order, BigDecimal currentPrice) {
        BigDecimal priceMargin = currentPrice.multiply(BigDecimal.valueOf(mockProperties.getOrders().getPriceMargin()));
        
        boolean shouldExecute;
        String condition;
        
        if (order.getSide() == Order.OrderSide.BUY) {
            // Buy order: execute if current price is at or below order price (+ margin)
            BigDecimal maxAcceptablePrice = order.getPrice().add(priceMargin);
            shouldExecute = currentPrice.compareTo(maxAcceptablePrice) <= 0;
            condition = String.format("currentPrice(%.2f) <= orderPrice(%.2f) + margin(%.2f) = %.2f", 
                currentPrice.doubleValue(), order.getPrice().doubleValue(), 
                priceMargin.doubleValue(), maxAcceptablePrice.doubleValue());
        } else {
            // Sell order: execute if current price is at or above order price (- margin)
            BigDecimal minAcceptablePrice = order.getPrice().subtract(priceMargin);
            shouldExecute = currentPrice.compareTo(minAcceptablePrice) >= 0;
            condition = String.format("currentPrice(%.2f) >= orderPrice(%.2f) - margin(%.2f) = %.2f", 
                currentPrice.doubleValue(), order.getPrice().doubleValue(), 
                priceMargin.doubleValue(), minAcceptablePrice.doubleValue());
        }
        
        log.debug("Order execution check for {}: {} → {}", 
            order.getId(), condition, shouldExecute ? "EXECUTE" : "KEEP_PENDING");
        
        return shouldExecute;
    }
    
    private BigDecimal calculateSlippage(BigDecimal price) {
        double slippageRate = mockProperties.getMarketConditions().getSlippageRate();
        return price.multiply(BigDecimal.valueOf(slippageRate * ThreadLocalRandom.current().nextDouble()));
    }

    @Override
    public Order cancelOrder(String orderId) {
        log.info("Cancelling order: {}", orderId);
        
        Order order = orders.get(orderId);
        if (order == null) {
            throw new IllegalArgumentException("Order not found: " + orderId);
        }
        
        if (order.getStatus() == Order.OrderStatus.FILLED) {
            throw new IllegalStateException("Cannot cancel filled order: " + orderId);
        }
        
        order.updateStatus(Order.OrderStatus.CANCELLED);
        
        // Notifica listeners sobre o cancelamento
        notifyOrderCancelled(order);
        
        return order;
    }

    @Override
    public Order getOrderStatus(String orderId) {
        log.debug("Getting status for order: {}", orderId);
        
        Order order = orders.get(orderId);
        if (order == null) {
            throw new IllegalArgumentException("Order not found: " + orderId);
        }
        
        return order;
    }

    @Override
    public List<Order> getActiveOrders() {
        log.debug("Getting active orders");
        
        return orders.values().stream()
            .filter(order -> order.getStatus() == Order.OrderStatus.PENDING || 
                           order.getStatus() == Order.OrderStatus.PARTIALLY_FILLED)
            .toList();
    }

    @Override
    public Price getCurrentPrice(TradingPair tradingPair) {
        log.debug("Getting current price for: {}", tradingPair.getSymbol());
        
        BigDecimal currentPrice = getCurrentMarketPrice(tradingPair);
        return new Price(currentPrice.toString());
    }
    
    private BigDecimal getCurrentMarketPrice(TradingPair tradingPair) {
        if (mockProperties.isStrictMode()) {
            // In strict mode, return exact values from files
            PriceData currentPriceData = marketDataLoader.getCurrentPriceData(tradingPair.getSymbol());
            if (currentPriceData != null) {
                return currentPriceData.getPriceAsBigDecimal();
            }
        }
        
        // Fallback to cached prices
        Price cachedPrice = prices.get(tradingPair);
        if (cachedPrice != null) {
            if (mockProperties.isStrictMode()) {
                // No fluctuation in strict mode
                return cachedPrice.getValue();
            } else {
                // Add fluctuation in non-strict mode
                return addPriceFluctuation(cachedPrice.getValue());
            }
        }
        
        throw new IllegalArgumentException("Price not available for trading pair: " + tradingPair.getSymbol());
    }
    
    private BigDecimal addPriceFluctuation(BigDecimal basePrice) {
        if (mockProperties.isStrictMode()) {
            // No fluctuation in strict mode
            return basePrice;
        }
        
        double volatility = mockProperties.getPriceSimulation().getVolatility();
        double fluctuation = ThreadLocalRandom.current().nextDouble(-volatility, volatility);
        BigDecimal fluctuationMultiplier = BigDecimal.ONE.add(BigDecimal.valueOf(fluctuation));
        return basePrice.multiply(fluctuationMultiplier).setScale(8, RoundingMode.HALF_UP);
    }

    private void initializePrices() {
        // Initialize prices from market data files
        marketDataLoader.getAllMarketData().forEach((symbol, priceDataList) -> {
            try {
                if (!priceDataList.isEmpty()) {
                    TradingPair pair = parseSymbolToTradingPair(symbol);
                    // Use the first price from the file as initial price
                    PriceData firstPrice = priceDataList.get(0);
                    Price price = new Price(firstPrice.getPrice());
                    prices.put(pair, price);
                    log.debug("Initialized price for {}: {} (from file)", symbol, price.getValue());
                }
            } catch (Exception e) {
                log.warn("Failed to initialize price for {}: {}", symbol, e.getMessage());
            }
        });
        
        // Fallback prices if no market data available
        if (prices.isEmpty()) {
            prices.put(new TradingPair("BTC", "USDT"), new Price("45000"));
            prices.put(new TradingPair("ETH", "USDT"), new Price("2900"));
            prices.put(new TradingPair("ADA", "USDT"), new Price("1.5"));
            log.warn("Using fallback prices - no market data files found");
        }
        
        log.info("Initialized {} trading pairs with {} mode prices", 
            prices.size(), mockProperties.isStrictMode() ? "strict" : "simulation");
    }

    // Method for testing purposes to set specific prices
    public void setPrice(TradingPair tradingPair, Price price) {
        prices.put(tradingPair, price);
    }

    // Method for testing purposes to clear orders
    public void clearOrders() {
        orders.clear();
    }
    
    private TradingPair parseSymbolToTradingPair(String symbol) {
        // Convert BTCUSDT format to BTC/USDT format
        if (symbol.endsWith("USDT")) {
            String base = symbol.substring(0, symbol.length() - 4);
            return new TradingPair(base, "USDT");
        } else if (symbol.endsWith("USD")) {
            String base = symbol.substring(0, symbol.length() - 3);
            return new TradingPair(base, "USD");
        } else if (symbol.endsWith("BTC")) {
            String base = symbol.substring(0, symbol.length() - 3);
            return new TradingPair(base, "BTC");
        } else if (symbol.endsWith("ETH")) {
            String base = symbol.substring(0, symbol.length() - 3);
            return new TradingPair(base, "ETH");
        }
        
        // Fallback: try to parse as is if it contains "/"
        if (symbol.contains("/")) {
            return new TradingPair(symbol);
        }
        
        // Default fallback
        throw new IllegalArgumentException("Unable to parse trading pair symbol: " + symbol);
    }
    
    /**
     * Adiciona um listener para eventos de ordem.
     */
    public void addOrderEventListener(OrderEventListener listener) {
        orderEventListeners.add(listener);
        log.debug("Added order event listener: {}", listener.getClass().getSimpleName());
    }
    
    /**
     * Remove um listener de eventos de ordem.
     */
    public void removeOrderEventListener(OrderEventListener listener) {
        orderEventListeners.remove(listener);
        log.debug("Removed order event listener: {}", listener.getClass().getSimpleName());
    }
    
    /**
     * Notifica todos os listeners sobre uma mudança de status de ordem.
     */
    private void notifyOrderUpdate(Order order) {
        for (OrderEventListener listener : orderEventListeners) {
            try {
                listener.onOrderUpdated(order);
                
                // Notificações específicas baseadas no status
                if (order.getStatus() == Order.OrderStatus.FILLED || 
                    order.getStatus() == Order.OrderStatus.PARTIALLY_FILLED) {
                    listener.onOrderFilled(order);
                }
            } catch (Exception e) {
                log.error("Error notifying order event listener: {}", e.getMessage(), e);
            }
        }
    }
    
    /**
     * Notifica todos os listeners sobre o cancelamento de uma ordem.
     */
    private void notifyOrderCancelled(Order order) {
        for (OrderEventListener listener : orderEventListeners) {
            try {
                listener.onOrderCancelled(order);
            } catch (Exception e) {
                log.error("Error notifying order cancellation listener: {}", e.getMessage(), e);
            }
        }
    }
    
    /**
     * Inicia o processador de ordens pendentes.
     */
    private void startOrderProcessor() {
        orderProcessor.scheduleWithFixedDelay(this::processPendingOrders, 2, 3, TimeUnit.SECONDS);
        log.info("Started order processor - checking pending orders every 3 seconds");
    }
    
    /**
     * Processa ordens pendentes e parcialmente executadas periodicamente para tentar finalizá-las.
     */
    private void processPendingOrders() {
        List<Order> activeOrders = orders.values().stream()
            .filter(order -> order.getStatus() == Order.OrderStatus.PENDING || 
                           order.getStatus() == Order.OrderStatus.PARTIALLY_FILLED)
            .toList();
            
        if (!activeOrders.isEmpty()) {
            log.debug("🔄 Processing {} active orders (pending + partial)...", activeOrders.size());
            
            for (Order order : activeOrders) {
                try {
                    BigDecimal currentPrice = getCurrentMarketPrice(order.getTradingPair());
                    
                    if (shouldExecuteLimitOrder(order, currentPrice)) {
                        // Lógica diferente baseada no status atual da ordem
                        if (order.getStatus() == Order.OrderStatus.PARTIALLY_FILLED) {
                            // Ordens parciais têm 80% de chance de serem completamente executadas
                            if (ThreadLocalRandom.current().nextDouble() < 0.8) {
                                order.updateStatus(Order.OrderStatus.FILLED);
                                log.info("✅ Partially filled order completed: {} {} {} at {} (current: {})", 
                                    order.getId(), order.getSide(), order.getTradingPair().getSymbol(), 
                                    order.getPrice(), currentPrice);
                            } else {
                                log.debug("⏳ Partially filled order remains partial: {} {} {} at {} (current: {})", 
                                    order.getId(), order.getSide(), order.getTradingPair().getSymbol(), 
                                    order.getPrice(), currentPrice);
                            }
                        } else {
                            // Ordens PENDING: usar taxa normal de partial fill
                            if (ThreadLocalRandom.current().nextDouble() < mockProperties.getOrders().getPartialFillRate()) {
                                order.updateStatus(Order.OrderStatus.PARTIALLY_FILLED);
                                log.info("✅ Pending order partially filled: {} {} {} at {} (current: {})", 
                                    order.getId(), order.getSide(), order.getTradingPair().getSymbol(), 
                                    order.getPrice(), currentPrice);
                            } else {
                                order.updateStatus(Order.OrderStatus.FILLED);
                                log.info("✅ Pending order filled: {} {} {} at {} (current: {})", 
                                    order.getId(), order.getSide(), order.getTradingPair().getSymbol(), 
                                    order.getPrice(), currentPrice);
                            }
                        }
                        
                        // Notifica listeners sobre a mudança
                        notifyOrderUpdate(order);
                    }
                } catch (Exception e) {
                    log.error("Error processing pending order {}: {}", order.getId(), e.getMessage());
                }
            }
            
            // Log status summary
            long stillPending = orders.values().stream()
                .filter(order -> order.getStatus() == Order.OrderStatus.PENDING)
                .count();
            long partiallyFilled = orders.values().stream()
                .filter(order -> order.getStatus() == Order.OrderStatus.PARTIALLY_FILLED)
                .count();
            long filled = orders.values().stream()
                .filter(order -> order.getStatus() == Order.OrderStatus.FILLED)
                .count();
            
            log.info("📊 Order Status - Pending: {}, Partial: {}, Filled: {}, Total: {}", 
                stillPending, partiallyFilled, filled, orders.size());
        }
    }
    
    /**
     * Retorna estatísticas de ordens para métricas
     */
    public OrderStatistics getOrderStatistics() {
        long pending = orders.values().stream()
            .filter(order -> order.getStatus() == Order.OrderStatus.PENDING)
            .count();
        long partiallyFilled = orders.values().stream()
            .filter(order -> order.getStatus() == Order.OrderStatus.PARTIALLY_FILLED)
            .count();
        long filled = orders.values().stream()
            .filter(order -> order.getStatus() == Order.OrderStatus.FILLED)
            .count();
        long cancelled = orders.values().stream()
            .filter(order -> order.getStatus() == Order.OrderStatus.CANCELLED)
            .count();
            
        return new OrderStatistics(
            orders.size(),                    // totalOrders
            (int) filled,                     // successfulOrders
            (int) cancelled,                  // failedOrders  
            (int) pending                     // pendingOrders
        );
    }
    
    /**
     * Classe para estatísticas de ordens
     */
    public static class OrderStatistics {
        public final int totalOrders;
        public final int successfulOrders;
        public final int failedOrders;
        public final int pendingOrders;
        
        public OrderStatistics(int totalOrders, int successfulOrders, int failedOrders, int pendingOrders) {
            this.totalOrders = totalOrders;
            this.successfulOrders = successfulOrders;
            this.failedOrders = failedOrders;
            this.pendingOrders = pendingOrders;
        }
    }
}