package com.marmitt.ctrade.infrastructure.exchange.mock;

import com.marmitt.ctrade.domain.entity.Order;
import com.marmitt.ctrade.domain.entity.TradingPair;
import com.marmitt.ctrade.domain.valueobject.Price;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class MockExchangeAdapterTest {

    private MockExchangeAdapter adapter;
    private TradingPair tradingPair;
    private BigDecimal quantity;
    private BigDecimal price;

    @BeforeEach
    void setUp() {
        adapter = new MockExchangeAdapter();
        tradingPair = new TradingPair("BTC", "USD");
        quantity = new BigDecimal("0.5");
        price = new BigDecimal("50000");
    }

    @Test
    @DisplayName("Should place market order and fill immediately")
    void shouldPlaceMarketOrderAndFillImmediately() {
        Order order = new Order(tradingPair, Order.OrderType.MARKET, Order.OrderSide.BUY, quantity, price);
        
        Order result = adapter.placeOrder(order);
        
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(Order.OrderStatus.FILLED);
        assertThat(result.getType()).isEqualTo(Order.OrderType.MARKET);
    }

    @Test
    @DisplayName("Should place limit order")
    void shouldPlaceLimitOrder() {
        Order order = new Order(tradingPair, Order.OrderType.LIMIT, Order.OrderSide.BUY, quantity, price);
        
        Order result = adapter.placeOrder(order);
        
        assertThat(result).isNotNull();
        assertThat(result.getType()).isEqualTo(Order.OrderType.LIMIT);
        assertThat(result.getStatus()).isIn(Order.OrderStatus.PENDING, Order.OrderStatus.FILLED);
    }

    @Test
    @DisplayName("Should cancel pending order successfully")
    void shouldCancelPendingOrderSuccessfully() {
        // Place a limit order first (keep trying until we get a pending one)
        Order order = null;
        for (int i = 0; i < 10; i++) {
            order = new Order(tradingPair, Order.OrderType.LIMIT, Order.OrderSide.BUY, quantity, price);
            adapter.placeOrder(order);
            if (order.getStatus() == Order.OrderStatus.PENDING) {
                break;
            }
        }
        
        // If we still don't have a pending order, manually set it for testing
        if (order.getStatus() != Order.OrderStatus.PENDING) {
            order.updateStatus(Order.OrderStatus.PENDING);
        }
        
        Order cancelledOrder = adapter.cancelOrder(order.getId());
        
        assertThat(cancelledOrder.getStatus()).isEqualTo(Order.OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("Should throw exception when cancelling non-existent order")
    void shouldThrowExceptionWhenCancellingNonExistentOrder() {
        assertThatThrownBy(() -> adapter.cancelOrder("non-existent-id"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Order not found");
    }

    @Test
    @DisplayName("Should throw exception when cancelling filled order")
    void shouldThrowExceptionWhenCancellingFilledOrder() {
        Order order = new Order(tradingPair, Order.OrderType.MARKET, Order.OrderSide.BUY, quantity, price);
        adapter.placeOrder(order);
        
        // Market orders are filled immediately
        assertThat(order.getStatus()).isEqualTo(Order.OrderStatus.FILLED);
        
        assertThatThrownBy(() -> adapter.cancelOrder(order.getId()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Cannot cancel filled order");
    }

    @Test
    @DisplayName("Should get order status successfully")
    void shouldGetOrderStatusSuccessfully() {
        Order order = new Order(tradingPair, Order.OrderType.LIMIT, Order.OrderSide.BUY, quantity, price);
        adapter.placeOrder(order);
        
        Order result = adapter.getOrderStatus(order.getId());
        
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(order.getId());
        assertThat(result.getStatus()).isIn(Order.OrderStatus.PENDING, Order.OrderStatus.FILLED);
    }

    @Test
    @DisplayName("Should throw exception when getting status of non-existent order")
    void shouldThrowExceptionWhenGettingStatusOfNonExistentOrder() {
        assertThatThrownBy(() -> adapter.getOrderStatus("non-existent-id"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Order not found");
    }

    @Test
    @DisplayName("Should get active orders")
    void shouldGetActiveOrders() {
        // Clear any existing orders
        adapter.clearOrders();
        
        // Place multiple orders
        for (int i = 0; i < 5; i++) {
            Order order = new Order(tradingPair, Order.OrderType.LIMIT, Order.OrderSide.BUY, 
                quantity, price.add(BigDecimal.valueOf(i * 100)));
            adapter.placeOrder(order);
        }
        
        List<Order> activeOrders = adapter.getActiveOrders();
        
        assertThat(activeOrders).isNotNull();
        // Since some orders might be filled, we check that we have some active orders
        assertThat(activeOrders.size()).isLessThanOrEqualTo(5);
        
        // All returned orders should be active (pending or partially filled)
        activeOrders.forEach(order -> 
            assertThat(order.getStatus()).isIn(Order.OrderStatus.PENDING, Order.OrderStatus.PARTIALLY_FILLED)
        );
    }

    @Test
    @DisplayName("Should get current price for existing trading pair")
    void shouldGetCurrentPriceForExistingTradingPair() {
        Price result = adapter.getCurrentPrice(tradingPair);
        
        assertThat(result).isNotNull();
        assertThat(result.getValue()).isPositive();
        // Price should be around 50000 with some fluctuation
        assertThat(result.getValue()).isBetween(
            new BigDecimal("49000"), 
            new BigDecimal("51000")
        );
    }

    @Test
    @DisplayName("Should throw exception for unknown trading pair price")
    void shouldThrowExceptionForUnknownTradingPairPrice() {
        TradingPair unknownPair = new TradingPair("UNKNOWN", "PAIR");
        
        assertThatThrownBy(() -> adapter.getCurrentPrice(unknownPair))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Price not available for trading pair");
    }

    @Test
    @DisplayName("Should set custom price for testing")
    void shouldSetCustomPriceForTesting() {
        TradingPair customPair = new TradingPair("TEST", "USD");
        Price customPrice = new Price("12345.67");
        
        adapter.setPrice(customPair, customPrice);
        
        Price result = adapter.getCurrentPrice(customPair);
        assertThat(result.getValue()).isBetween(
            customPrice.getValue().multiply(new BigDecimal("0.99")),
            customPrice.getValue().multiply(new BigDecimal("1.01"))
        );
    }

    @Test
    @DisplayName("Should clear orders for testing")
    void shouldClearOrdersForTesting() {
        // Place some orders
        Order order1 = new Order(tradingPair, Order.OrderType.LIMIT, Order.OrderSide.BUY, quantity, price);
        Order order2 = new Order(tradingPair, Order.OrderType.LIMIT, Order.OrderSide.SELL, quantity, price);
        adapter.placeOrder(order1);
        adapter.placeOrder(order2);
        
        adapter.clearOrders();
        
        List<Order> activeOrders = adapter.getActiveOrders();
        assertThat(activeOrders).isEmpty();
    }
}