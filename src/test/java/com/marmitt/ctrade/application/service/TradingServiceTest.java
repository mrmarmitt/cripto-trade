package com.marmitt.ctrade.application.service;

import com.marmitt.ctrade.domain.entity.Order;
import com.marmitt.ctrade.domain.entity.TradingPair;
import com.marmitt.ctrade.domain.port.ExchangePort;
import com.marmitt.ctrade.domain.valueobject.Price;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TradingServiceTest {

    @Mock
    private ExchangePort exchangePort;

    private TradingService tradingService;
    private TradingPair tradingPair;
    private BigDecimal quantity;
    private BigDecimal price;

    @BeforeEach
    void setUp() {
        tradingService = new TradingService(exchangePort);
        tradingPair = new TradingPair("BTC", "USD");
        quantity = new BigDecimal("0.5");
        price = new BigDecimal("50000");
    }

    @Test
    @DisplayName("Should place buy order successfully")
    void shouldPlaceBuyOrderSuccessfully() {
        Order expectedOrder = new Order(tradingPair, Order.OrderType.LIMIT, Order.OrderSide.BUY, quantity, price);
        when(exchangePort.placeOrder(any(Order.class))).thenReturn(expectedOrder);

        Order result = tradingService.placeBuyOrder(tradingPair, quantity, price);

        assertThat(result).isNotNull();
        assertThat(result.getSide()).isEqualTo(Order.OrderSide.BUY);
        assertThat(result.getType()).isEqualTo(Order.OrderType.LIMIT);
        verify(exchangePort).placeOrder(argThat(order -> 
            order.getSide() == Order.OrderSide.BUY &&
            order.getType() == Order.OrderType.LIMIT &&
            order.getTradingPair().equals(tradingPair) &&
            order.getQuantity().equals(quantity) &&
            order.getPrice().equals(price)
        ));
    }

    @Test
    @DisplayName("Should place sell order successfully")
    void shouldPlaceSellOrderSuccessfully() {
        Order expectedOrder = new Order(tradingPair, Order.OrderType.LIMIT, Order.OrderSide.SELL, quantity, price);
        when(exchangePort.placeOrder(any(Order.class))).thenReturn(expectedOrder);

        Order result = tradingService.placeSellOrder(tradingPair, quantity, price);

        assertThat(result).isNotNull();
        assertThat(result.getSide()).isEqualTo(Order.OrderSide.SELL);
        assertThat(result.getType()).isEqualTo(Order.OrderType.LIMIT);
        verify(exchangePort).placeOrder(argThat(order -> 
            order.getSide() == Order.OrderSide.SELL &&
            order.getType() == Order.OrderType.LIMIT
        ));
    }

    @Test
    @DisplayName("Should place market buy order successfully")
    void shouldPlaceMarketBuyOrderSuccessfully() {
        Price currentPrice = new Price("51000");
        when(exchangePort.getCurrentPrice(tradingPair)).thenReturn(currentPrice);
        
        Order expectedOrder = new Order(tradingPair, Order.OrderType.MARKET, Order.OrderSide.BUY, quantity, currentPrice.getValue());
        when(exchangePort.placeOrder(any(Order.class))).thenReturn(expectedOrder);

        Order result = tradingService.placeMarketBuyOrder(tradingPair, quantity);

        assertThat(result).isNotNull();
        assertThat(result.getSide()).isEqualTo(Order.OrderSide.BUY);
        assertThat(result.getType()).isEqualTo(Order.OrderType.MARKET);
        verify(exchangePort).getCurrentPrice(tradingPair);
        verify(exchangePort).placeOrder(argThat(order -> 
            order.getSide() == Order.OrderSide.BUY &&
            order.getType() == Order.OrderType.MARKET &&
            order.getPrice().equals(currentPrice.getValue())
        ));
    }

    @Test
    @DisplayName("Should cancel order successfully")
    void shouldCancelOrderSuccessfully() {
        String orderId = "order123";
        Order cancelledOrder = new Order(tradingPair, Order.OrderType.LIMIT, Order.OrderSide.BUY, quantity, price);
        cancelledOrder.updateStatus(Order.OrderStatus.CANCELLED);
        when(exchangePort.cancelOrder(orderId)).thenReturn(cancelledOrder);

        Order result = tradingService.cancelOrder(orderId);

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(Order.OrderStatus.CANCELLED);
        verify(exchangePort).cancelOrder(orderId);
    }

    @Test
    @DisplayName("Should get order status successfully")
    void shouldGetOrderStatusSuccessfully() {
        String orderId = "order123";
        Order order = new Order(tradingPair, Order.OrderType.LIMIT, Order.OrderSide.BUY, quantity, price);
        when(exchangePort.getOrderStatus(orderId)).thenReturn(order);

        Order result = tradingService.getOrderStatus(orderId);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(order.getId());
        verify(exchangePort).getOrderStatus(orderId);
    }

    @Test
    @DisplayName("Should get active orders successfully")
    void shouldGetActiveOrdersSuccessfully() {
        List<Order> activeOrders = Arrays.asList(
            new Order(tradingPair, Order.OrderType.LIMIT, Order.OrderSide.BUY, quantity, price),
            new Order(tradingPair, Order.OrderType.LIMIT, Order.OrderSide.SELL, quantity, price)
        );
        when(exchangePort.getActiveOrders()).thenReturn(activeOrders);

        List<Order> result = tradingService.getActiveOrders();

        assertThat(result).hasSize(2);
        assertThat(result).containsExactlyElementsOf(activeOrders);
        verify(exchangePort).getActiveOrders();
    }

    @Test
    @DisplayName("Should get current price successfully")
    void shouldGetCurrentPriceSuccessfully() {
        Price expectedPrice = new Price("50000");
        when(exchangePort.getCurrentPrice(tradingPair)).thenReturn(expectedPrice);

        Price result = tradingService.getCurrentPrice(tradingPair);

        assertThat(result).isEqualTo(expectedPrice);
        verify(exchangePort).getCurrentPrice(tradingPair);
    }

    @Test
    @DisplayName("Should throw exception for null quantity in buy order")
    void shouldThrowExceptionForNullQuantityInBuyOrder() {
        assertThatThrownBy(() -> tradingService.placeBuyOrder(tradingPair, null, price))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Quantity must be positive");
    }

    @Test
    @DisplayName("Should throw exception for zero quantity in buy order")
    void shouldThrowExceptionForZeroQuantityInBuyOrder() {
        assertThatThrownBy(() -> tradingService.placeBuyOrder(tradingPair, BigDecimal.ZERO, price))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Quantity must be positive");
    }

    @Test
    @DisplayName("Should throw exception for negative quantity in buy order")
    void shouldThrowExceptionForNegativeQuantityInBuyOrder() {
        assertThatThrownBy(() -> tradingService.placeBuyOrder(tradingPair, new BigDecimal("-1"), price))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Quantity must be positive");
    }

    @Test
    @DisplayName("Should throw exception for null price in buy order")
    void shouldThrowExceptionForNullPriceInBuyOrder() {
        assertThatThrownBy(() -> tradingService.placeBuyOrder(tradingPair, quantity, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Price must be positive");
    }

    @Test
    @DisplayName("Should throw exception for null quantity in market buy order")
    void shouldThrowExceptionForNullQuantityInMarketBuyOrder() {
        assertThatThrownBy(() -> tradingService.placeMarketBuyOrder(tradingPair, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Quantity must be positive");
    }

    @Test
    @DisplayName("Should throw exception for null order ID in cancel order")
    void shouldThrowExceptionForNullOrderIdInCancelOrder() {
        assertThatThrownBy(() -> tradingService.cancelOrder(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Order ID cannot be null or empty");
    }

    @Test
    @DisplayName("Should throw exception for empty order ID in cancel order")
    void shouldThrowExceptionForEmptyOrderIdInCancelOrder() {
        assertThatThrownBy(() -> tradingService.cancelOrder(""))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Order ID cannot be null or empty");
    }

    @Test
    @DisplayName("Should throw exception for null trading pair in get current price")
    void shouldThrowExceptionForNullTradingPairInGetCurrentPrice() {
        assertThatThrownBy(() -> tradingService.getCurrentPrice(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Trading pair cannot be null");
    }
}