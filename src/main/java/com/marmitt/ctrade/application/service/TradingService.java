package com.marmitt.ctrade.application.service;

import com.marmitt.ctrade.domain.entity.Order;
import com.marmitt.ctrade.domain.entity.TradingPair;
import com.marmitt.ctrade.domain.port.ExchangePort;
import com.marmitt.ctrade.domain.valueobject.Price;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TradingService {

    private final ExchangePort exchangePort;

    public Order placeBuyOrder(TradingPair tradingPair, BigDecimal quantity, BigDecimal price) {
        validateOrderParameters(quantity, price);
        
        Order order = new Order(tradingPair, Order.OrderType.LIMIT, Order.OrderSide.BUY, quantity, price);
        return exchangePort.placeOrder(order);
    }

    public Order placeSellOrder(TradingPair tradingPair, BigDecimal quantity, BigDecimal price) {
        validateOrderParameters(quantity, price);
        
        Order order = new Order(tradingPair, Order.OrderType.LIMIT, Order.OrderSide.SELL, quantity, price);
        return exchangePort.placeOrder(order);
    }

    public Order placeMarketBuyOrder(TradingPair tradingPair, BigDecimal quantity) {
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        
        Price currentPrice = exchangePort.getCurrentPrice(tradingPair);
        Order order = new Order(tradingPair, Order.OrderType.MARKET, Order.OrderSide.BUY, quantity, currentPrice.getValue());
        return exchangePort.placeOrder(order);
    }

    public Order cancelOrder(String orderId) {
        if (orderId == null || orderId.trim().isEmpty()) {
            throw new IllegalArgumentException("Order ID cannot be null or empty");
        }
        
        return exchangePort.cancelOrder(orderId);
    }

    public Order getOrderStatus(String orderId) {
        if (orderId == null || orderId.trim().isEmpty()) {
            throw new IllegalArgumentException("Order ID cannot be null or empty");
        }
        
        return exchangePort.getOrderStatus(orderId);
    }

    public List<Order> getActiveOrders() {
        return exchangePort.getActiveOrders();
    }

    public Price getCurrentPrice(TradingPair tradingPair) {
        if (tradingPair == null) {
            throw new IllegalArgumentException("Trading pair cannot be null");
        }
        
        return exchangePort.getCurrentPrice(tradingPair);
    }

    private void validateOrderParameters(BigDecimal quantity, BigDecimal price) {
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Price must be positive");
        }
    }
}