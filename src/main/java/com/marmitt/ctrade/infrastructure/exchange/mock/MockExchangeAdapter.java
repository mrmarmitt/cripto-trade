package com.marmitt.ctrade.infrastructure.exchange.mock;

import com.marmitt.ctrade.domain.entity.Order;
import com.marmitt.ctrade.domain.entity.TradingPair;
import com.marmitt.ctrade.domain.port.ExchangePort;
import com.marmitt.ctrade.domain.valueobject.Price;
import com.marmitt.ctrade.infrastructure.config.MockExchangeProperties;
import com.marmitt.ctrade.infrastructure.exchange.mock.dto.PriceData;
import com.marmitt.ctrade.infrastructure.exchange.mock.service.MockMarketDataLoader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Component
@RequiredArgsConstructor
public class MockExchangeAdapter implements ExchangePort {

    private final MockExchangeProperties mockProperties;
    private final MockMarketDataLoader marketDataLoader;
    private final Map<String, Order> orders = new ConcurrentHashMap<>();
    private final Map<TradingPair, Price> prices = new ConcurrentHashMap<>();
    
    @PostConstruct
    public void initialize() {
        initializePrices();
    }

    @Override
    public Order placeOrder(Order order) {
        log.info("Placing order: {} {} {} {} at {}", 
            order.getSide(), order.getQuantity(), order.getTradingPair().getSymbol(), 
            order.getType(), order.getPrice());
        
        // Check order acceptance rate
        if (!shouldAcceptOrder()) {
            order.updateStatus(Order.OrderStatus.CANCELLED);
            log.info("Order rejected: {} (acceptance rate: {})", 
                order.getId(), mockProperties.getOrders().getAcceptanceRate());
            orders.put(order.getId(), order);
            return order;
        }
        
        // Process order based on type
        if (order.getType() == Order.OrderType.MARKET) {
            processMarketOrder(order);
        } else {
            processLimitOrder(order);
        }
        
        orders.put(order.getId(), order);
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
        
        if (shouldExecuteLimitOrder(order, currentPrice)) {
            // Check for partial fill
            if (ThreadLocalRandom.current().nextDouble() < mockProperties.getOrders().getPartialFillRate()) {
                order.updateStatus(Order.OrderStatus.PARTIALLY_FILLED);
                log.info("Limit order partially filled: {} at {}", order.getId(), order.getPrice());
            } else {
                order.updateStatus(Order.OrderStatus.FILLED);
                log.info("Limit order filled: {} at {}", order.getId(), order.getPrice());
            }
        } else {
            // Check if order should remain open or be executed anyway
            if (ThreadLocalRandom.current().nextDouble() < mockProperties.getOrders().getExecutionRate()) {
                order.updateStatus(Order.OrderStatus.FILLED);
                log.info("Limit order executed (forced): {} at {}", order.getId(), order.getPrice());
            } else {
                order.updateStatus(Order.OrderStatus.PENDING);
                log.info("Limit order pending: {} (current price: {}, order price: {})", 
                    order.getId(), currentPrice, order.getPrice());
            }
        }
    }
    
    private boolean shouldExecuteLimitOrder(Order order, BigDecimal currentPrice) {
        BigDecimal priceMargin = currentPrice.multiply(BigDecimal.valueOf(mockProperties.getOrders().getPriceMargin()));
        
        if (order.getSide() == Order.OrderSide.BUY) {
            // Buy order: execute if current price is at or below order price (+ margin)
            return currentPrice.compareTo(order.getPrice().add(priceMargin)) <= 0;
        } else {
            // Sell order: execute if current price is at or above order price (- margin)
            return currentPrice.compareTo(order.getPrice().subtract(priceMargin)) >= 0;
        }
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
}