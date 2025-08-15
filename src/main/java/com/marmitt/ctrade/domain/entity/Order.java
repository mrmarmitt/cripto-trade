package com.marmitt.ctrade.domain.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Order {
    private String id;
    private TradingPair tradingPair;
    private OrderType type;
    private OrderSide side;
    private BigDecimal quantity;
    private BigDecimal price;
    private OrderStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Order(TradingPair tradingPair, OrderType type, OrderSide side, BigDecimal quantity, BigDecimal price) {
        this.id = UUID.randomUUID().toString();
        this.tradingPair = tradingPair;
        this.type = type;
        this.side = side;
        this.quantity = quantity;
        this.price = price;
        this.status = OrderStatus.PENDING;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        
        validateOrder();
    }

    private void validateOrder() {
        if (tradingPair == null) {
            throw new IllegalArgumentException("Trading pair cannot be null");
        }
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Price must be positive");
        }
    }

    public void updateStatus(OrderStatus newStatus) {
        this.status = newStatus;
        this.updatedAt = LocalDateTime.now();
    }

    public BigDecimal getTotalValue() {
        return quantity.multiply(price);
    }

    public enum OrderType {
        MARKET, LIMIT
    }

    public enum OrderSide {
        BUY, SELL
    }

    public enum OrderStatus {
        PENDING, FILLED, CANCELLED, PARTIALLY_FILLED
    }
}