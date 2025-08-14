package com.marmitt.ctrade.domain.entity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

class OrderTest {

    private TradingPair tradingPair;
    private BigDecimal quantity;
    private BigDecimal price;

    @BeforeEach
    void setUp() {
        tradingPair = new TradingPair("BTC", "USD");
        quantity = new BigDecimal("0.5");
        price = new BigDecimal("50000");
    }

    @Test
    @DisplayName("Should create valid buy order")
    void shouldCreateValidBuyOrder() {
        Order order = new Order(tradingPair, Order.OrderType.LIMIT, Order.OrderSide.BUY, quantity, price);
        
        assertThat(order.getId()).isNotNull();
        assertThat(order.getTradingPair()).isEqualTo(tradingPair);
        assertThat(order.getType()).isEqualTo(Order.OrderType.LIMIT);
        assertThat(order.getSide()).isEqualTo(Order.OrderSide.BUY);
        assertThat(order.getQuantity()).isEqualTo(quantity);
        assertThat(order.getPrice()).isEqualTo(price);
        assertThat(order.getStatus()).isEqualTo(Order.OrderStatus.PENDING);
        assertThat(order.getCreatedAt()).isNotNull();
        assertThat(order.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should create valid sell order")
    void shouldCreateValidSellOrder() {
        Order order = new Order(tradingPair, Order.OrderType.MARKET, Order.OrderSide.SELL, quantity, price);
        
        assertThat(order.getSide()).isEqualTo(Order.OrderSide.SELL);
        assertThat(order.getType()).isEqualTo(Order.OrderType.MARKET);
    }

    @Test
    @DisplayName("Should calculate total value correctly")
    void shouldCalculateTotalValueCorrectly() {
        Order order = new Order(tradingPair, Order.OrderType.LIMIT, Order.OrderSide.BUY, quantity, price);
        
        BigDecimal expectedTotal = quantity.multiply(price);
        assertThat(order.getTotalValue()).isEqualTo(expectedTotal);
    }

    @Test
    @DisplayName("Should update status and timestamp")
    void shouldUpdateStatusAndTimestamp() {
        Order order = new Order(tradingPair, Order.OrderType.LIMIT, Order.OrderSide.BUY, quantity, price);
        LocalDateTime originalUpdatedAt = order.getUpdatedAt();
        
        // Wait a small amount to ensure timestamp difference
        try {
            Thread.sleep(1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        order.updateStatus(Order.OrderStatus.FILLED);
        
        assertThat(order.getStatus()).isEqualTo(Order.OrderStatus.FILLED);
        assertThat(order.getUpdatedAt()).isAfter(originalUpdatedAt);
    }

    @Test
    @DisplayName("Should throw exception for null trading pair")
    void shouldThrowExceptionForNullTradingPair() {
        assertThatThrownBy(() -> 
            new Order(null, Order.OrderType.LIMIT, Order.OrderSide.BUY, quantity, price))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Trading pair cannot be null");
    }

    @Test
    @DisplayName("Should throw exception for null quantity")
    void shouldThrowExceptionForNullQuantity() {
        assertThatThrownBy(() -> 
            new Order(tradingPair, Order.OrderType.LIMIT, Order.OrderSide.BUY, null, price))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Quantity must be positive");
    }

    @Test
    @DisplayName("Should throw exception for zero quantity")
    void shouldThrowExceptionForZeroQuantity() {
        assertThatThrownBy(() -> 
            new Order(tradingPair, Order.OrderType.LIMIT, Order.OrderSide.BUY, BigDecimal.ZERO, price))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Quantity must be positive");
    }

    @Test
    @DisplayName("Should throw exception for negative quantity")
    void shouldThrowExceptionForNegativeQuantity() {
        assertThatThrownBy(() -> 
            new Order(tradingPair, Order.OrderType.LIMIT, Order.OrderSide.BUY, new BigDecimal("-1"), price))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Quantity must be positive");
    }

    @Test
    @DisplayName("Should throw exception for null price")
    void shouldThrowExceptionForNullPrice() {
        assertThatThrownBy(() -> 
            new Order(tradingPair, Order.OrderType.LIMIT, Order.OrderSide.BUY, quantity, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Price must be positive");
    }

    @Test
    @DisplayName("Should throw exception for zero price")
    void shouldThrowExceptionForZeroPrice() {
        assertThatThrownBy(() -> 
            new Order(tradingPair, Order.OrderType.LIMIT, Order.OrderSide.BUY, quantity, BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Price must be positive");
    }

    @Test
    @DisplayName("Should throw exception for negative price")
    void shouldThrowExceptionForNegativePrice() {
        assertThatThrownBy(() -> 
            new Order(tradingPair, Order.OrderType.LIMIT, Order.OrderSide.BUY, quantity, new BigDecimal("-1000")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Price must be positive");
    }
}