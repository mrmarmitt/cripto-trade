package com.marmitt.ctrade.infrastructure.adapter;

import com.marmitt.ctrade.domain.entity.Order;
import com.marmitt.ctrade.domain.entity.TradingPair;
import com.marmitt.ctrade.domain.port.ExchangePort;
import com.marmitt.ctrade.domain.valueobject.Price;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Component
public class MockExchangeAdapter implements ExchangePort {

    private final Map<String, Order> orders = new ConcurrentHashMap<>();
    private final Map<TradingPair, Price> prices = new ConcurrentHashMap<>();

    public MockExchangeAdapter() {
        initializePrices();
    }

    @Override
    public Order placeOrder(Order order) {
        log.info("Placing order: {} {} {} {} at {}", 
            order.getSide(), order.getQuantity(), order.getTradingPair().getSymbol(), 
            order.getType(), order.getPrice());
        
        // Simulate order processing
        if (order.getType() == Order.OrderType.MARKET) {
            order.updateStatus(Order.OrderStatus.FILLED);
        } else {
            // 80% chance of filling limit orders immediately for demo purposes
            if (ThreadLocalRandom.current().nextDouble() < 0.8) {
                order.updateStatus(Order.OrderStatus.FILLED);
            } else {
                order.updateStatus(Order.OrderStatus.PENDING);
            }
        }
        
        orders.put(order.getId(), order);
        return order;
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
        
        Price basePrice = prices.get(tradingPair);
        if (basePrice == null) {
            throw new IllegalArgumentException("Price not available for trading pair: " + tradingPair.getSymbol());
        }
        
        // Add some random price fluctuation (±1%)
        double fluctuation = ThreadLocalRandom.current().nextDouble(-0.01, 0.01);
        BigDecimal fluctuationMultiplier = BigDecimal.ONE.add(BigDecimal.valueOf(fluctuation));
        
        return basePrice.multiply(fluctuationMultiplier);
    }

    private void initializePrices() {
        // Initialize some common trading pairs with mock prices
        prices.put(new TradingPair("BTC", "USD"), new Price("50000"));
        prices.put(new TradingPair("ETH", "USD"), new Price("3000"));
        prices.put(new TradingPair("BTC", "EUR"), new Price("45000"));
        prices.put(new TradingPair("ETH", "EUR"), new Price("2700"));
        
        log.info("Initialized {} trading pairs with mock prices", prices.size());
    }

    // Method for testing purposes to set specific prices
    public void setPrice(TradingPair tradingPair, Price price) {
        prices.put(tradingPair, price);
    }

    // Method for testing purposes to clear orders
    public void clearOrders() {
        orders.clear();
    }
}