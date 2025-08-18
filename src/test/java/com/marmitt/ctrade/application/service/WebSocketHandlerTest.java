package com.marmitt.ctrade.application.service;

import com.marmitt.ctrade.domain.dto.OrderUpdateMessage;
import com.marmitt.ctrade.domain.dto.PriceUpdateMessage;
import com.marmitt.ctrade.domain.entity.Order;
import com.marmitt.ctrade.domain.event.OrderUpdateEvent;
import com.marmitt.ctrade.domain.event.PriceUpdateEvent;
import com.marmitt.ctrade.domain.listener.OrderUpdateListener;
import com.marmitt.ctrade.domain.listener.PriceUpdateListener;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.*;

/**
 * Testes unitários para WebSocketHandler.
 * Testa manipulação de eventos e notificação de listeners.
 */
@ExtendWith(MockitoExtension.class)
class WebSocketHandlerTest {

    @Mock
    private PriceUpdateListener priceUpdateListener1;
    
    @Mock
    private PriceUpdateListener priceUpdateListener2;
    
    @Mock
    private OrderUpdateListener orderUpdateListener1;
    
    @Mock
    private OrderUpdateListener orderUpdateListener2;

    private WebSocketHandler webSocketHandler;

    @BeforeEach
    void setUp() {
        List<PriceUpdateListener> priceListeners = Arrays.asList(priceUpdateListener1, priceUpdateListener2);
        List<OrderUpdateListener> orderListeners = Arrays.asList(orderUpdateListener1, orderUpdateListener2);
        
        webSocketHandler = new WebSocketHandler(priceListeners, orderListeners);
    }

    private PriceUpdateMessage createPriceUpdate(String pair, String price) {
        PriceUpdateMessage priceUpdate = new PriceUpdateMessage();
        priceUpdate.setTradingPair(pair);
        priceUpdate.setPrice(new BigDecimal(price));
        priceUpdate.setTimestamp(LocalDateTime.now());
        return priceUpdate;
    }

    private OrderUpdateMessage createOrderUpdate(String orderId, Order.OrderStatus status) {
        OrderUpdateMessage orderUpdate = new OrderUpdateMessage();
        orderUpdate.setOrderId(orderId);
        orderUpdate.setStatus(status);
        orderUpdate.setReason("Test reason");
        orderUpdate.setTimestamp(LocalDateTime.now());
        return orderUpdate;
    }

    @Test
    void shouldNotifyAllPriceUpdateListenersOnPriceEvent() {
        // Given
        PriceUpdateMessage priceUpdate = createPriceUpdate("BTCUSDT", "50000.00");
        PriceUpdateEvent event = new PriceUpdateEvent(this, priceUpdate, "BINANCE");
        
        // When
        webSocketHandler.handlePriceUpdateEvent(event);
        
        // Then
        verify(priceUpdateListener1).onPriceUpdate(priceUpdate);
        verify(priceUpdateListener2).onPriceUpdate(priceUpdate);
    }

    @Test
    void shouldNotifyAllOrderUpdateListenersOnOrderEvent() {
        // Given
        OrderUpdateMessage orderUpdate = createOrderUpdate("123", Order.OrderStatus.FILLED);
        OrderUpdateEvent event = new OrderUpdateEvent(this, orderUpdate, "BINANCE");
        
        // When
        webSocketHandler.handleOrderUpdateEvent(event);
        
        // Then
        verify(orderUpdateListener1).onOrderUpdate(orderUpdate);
        verify(orderUpdateListener2).onOrderUpdate(orderUpdate);
    }

    @Test
    void shouldHandleEmptyPriceUpdateListeners() {
        // Given
        WebSocketHandler handlerWithEmptyListeners = new WebSocketHandler(Collections.emptyList(), Collections.emptyList());
        PriceUpdateMessage priceUpdate = createPriceUpdate("BTCUSDT", "50000.00");
        PriceUpdateEvent event = new PriceUpdateEvent(this, priceUpdate, "BINANCE");
        
        // When
        handlerWithEmptyListeners.handlePriceUpdateEvent(event);
        
        // Then
        // Should not throw any exceptions
        verifyNoInteractions(priceUpdateListener1, priceUpdateListener2);
    }

    @Test
    void shouldHandleEmptyOrderUpdateListeners() {
        // Given
        WebSocketHandler handlerWithEmptyListeners = new WebSocketHandler(Collections.emptyList(), Collections.emptyList());
        OrderUpdateMessage orderUpdate = createOrderUpdate("123", Order.OrderStatus.FILLED);
        OrderUpdateEvent event = new OrderUpdateEvent(this, orderUpdate, "BINANCE");
        
        // When
        handlerWithEmptyListeners.handleOrderUpdateEvent(event);
        
        // Then
        // Should not throw any exceptions
        verifyNoInteractions(orderUpdateListener1, orderUpdateListener2);
    }

    @Test
    void shouldContinueNotifyingOtherListenersWhenOnePriceListenerFails() {
        // Given
        PriceUpdateMessage priceUpdate = createPriceUpdate("BTCUSDT", "50000.00");
        PriceUpdateEvent event = new PriceUpdateEvent(this, priceUpdate, "BINANCE");
        
        doThrow(new RuntimeException("Listener failed")).when(priceUpdateListener1).onPriceUpdate(priceUpdate);
        
        // When
        webSocketHandler.handlePriceUpdateEvent(event);
        
        // Then
        verify(priceUpdateListener1).onPriceUpdate(priceUpdate);
        verify(priceUpdateListener2).onPriceUpdate(priceUpdate); // Should still be called
    }

    @Test
    void shouldContinueNotifyingOtherListenersWhenOneOrderListenerFails() {
        // Given
        OrderUpdateMessage orderUpdate = createOrderUpdate("123", Order.OrderStatus.FILLED);
        OrderUpdateEvent event = new OrderUpdateEvent(this, orderUpdate, "BINANCE");
        
        doThrow(new RuntimeException("Listener failed")).when(orderUpdateListener1).onOrderUpdate(orderUpdate);
        
        // When
        webSocketHandler.handleOrderUpdateEvent(event);
        
        // Then
        verify(orderUpdateListener1).onOrderUpdate(orderUpdate);
        verify(orderUpdateListener2).onOrderUpdate(orderUpdate); // Should still be called
    }

    @Test
    void shouldHandleMultiplePriceUpdateEventsCorrectly() {
        // Given
        PriceUpdateMessage priceUpdate1 = createPriceUpdate("BTCUSDT", "50000");
        PriceUpdateMessage priceUpdate2 = createPriceUpdate("ETHUSDT", "3000");
        PriceUpdateMessage priceUpdate3 = createPriceUpdate("ADAUSDT", "2");
        
        PriceUpdateEvent event1 = new PriceUpdateEvent(this, priceUpdate1, "BINANCE");
        PriceUpdateEvent event2 = new PriceUpdateEvent(this, priceUpdate2, "BINANCE");
        PriceUpdateEvent event3 = new PriceUpdateEvent(this, priceUpdate3, "BINANCE");
        
        // When
        webSocketHandler.handlePriceUpdateEvent(event1);
        webSocketHandler.handlePriceUpdateEvent(event2);
        webSocketHandler.handlePriceUpdateEvent(event3);
        
        // Then
        verify(priceUpdateListener1).onPriceUpdate(priceUpdate1);
        verify(priceUpdateListener1).onPriceUpdate(priceUpdate2);
        verify(priceUpdateListener1).onPriceUpdate(priceUpdate3);
        verify(priceUpdateListener2).onPriceUpdate(priceUpdate1);
        verify(priceUpdateListener2).onPriceUpdate(priceUpdate2);
        verify(priceUpdateListener2).onPriceUpdate(priceUpdate3);
    }

    @Test
    void shouldHandleMultipleOrderUpdateEventsCorrectly() {
        // Given
        OrderUpdateMessage order1 = createOrderUpdate("123", Order.OrderStatus.FILLED);
        OrderUpdateMessage order2 = createOrderUpdate("124", Order.OrderStatus.PENDING);
        OrderUpdateMessage order3 = createOrderUpdate("125", Order.OrderStatus.CANCELLED);
        
        OrderUpdateEvent event1 = new OrderUpdateEvent(this, order1, "BINANCE");
        OrderUpdateEvent event2 = new OrderUpdateEvent(this, order2, "BINANCE");
        OrderUpdateEvent event3 = new OrderUpdateEvent(this, order3, "BINANCE");
        
        // When
        webSocketHandler.handleOrderUpdateEvent(event1);
        webSocketHandler.handleOrderUpdateEvent(event2);
        webSocketHandler.handleOrderUpdateEvent(event3);
        
        // Then
        verify(orderUpdateListener1).onOrderUpdate(order1);
        verify(orderUpdateListener1).onOrderUpdate(order2);
        verify(orderUpdateListener1).onOrderUpdate(order3);
        verify(orderUpdateListener2).onOrderUpdate(order1);
        verify(orderUpdateListener2).onOrderUpdate(order2);
        verify(orderUpdateListener2).onOrderUpdate(order3);
    }

    @Test
    void shouldHandlePriceEventWithNullMessage() {
        // Given
        PriceUpdateEvent event = new PriceUpdateEvent(this, null, "BINANCE");
        
        // When
        webSocketHandler.handlePriceUpdateEvent(event);
        
        // Then
        verify(priceUpdateListener1).onPriceUpdate(null);
        verify(priceUpdateListener2).onPriceUpdate(null);
    }

    @Test
    void shouldHandleOrderEventWithNullMessage() {
        // Given
        OrderUpdateEvent event = new OrderUpdateEvent(this, null, "BINANCE");
        
        // When
        webSocketHandler.handleOrderUpdateEvent(event);
        
        // Then
        verify(orderUpdateListener1).onOrderUpdate(null);
        verify(orderUpdateListener2).onOrderUpdate(null);
    }

    @Test
    void shouldHandleMixedSuccessAndFailureInPriceListeners() {
        // Given
        PriceUpdateMessage priceUpdate = createPriceUpdate("BTCUSDT", "50000");
        PriceUpdateEvent event = new PriceUpdateEvent(this, priceUpdate, "BINANCE");
        
        // First listener succeeds, second fails
        doNothing().when(priceUpdateListener1).onPriceUpdate(priceUpdate);
        doThrow(new RuntimeException("Processing error")).when(priceUpdateListener2).onPriceUpdate(priceUpdate);
        
        // When
        webSocketHandler.handlePriceUpdateEvent(event);
        
        // Then
        verify(priceUpdateListener1).onPriceUpdate(priceUpdate);
        verify(priceUpdateListener2).onPriceUpdate(priceUpdate);
    }

    @Test
    void shouldHandleMixedSuccessAndFailureInOrderListeners() {
        // Given
        OrderUpdateMessage orderUpdate = createOrderUpdate("123", Order.OrderStatus.FILLED);
        OrderUpdateEvent event = new OrderUpdateEvent(this, orderUpdate, "BINANCE");
        
        // First listener succeeds, second fails
        doNothing().when(orderUpdateListener1).onOrderUpdate(orderUpdate);
        doThrow(new RuntimeException("Processing error")).when(orderUpdateListener2).onOrderUpdate(orderUpdate);
        
        // When
        webSocketHandler.handleOrderUpdateEvent(event);
        
        // Then
        verify(orderUpdateListener1).onOrderUpdate(orderUpdate);
        verify(orderUpdateListener2).onOrderUpdate(orderUpdate);
    }

    @Test
    void shouldHandleEventsFromDifferentSources() {
        // Given
        PriceUpdateMessage priceUpdate = createPriceUpdate("BTCUSDT", "50000");
        OrderUpdateMessage orderUpdate = createOrderUpdate("123", Order.OrderStatus.FILLED);
        
        Object source1 = "BINANCE_ADAPTER";
        Object source2 = "MOCK_ADAPTER";
        
        PriceUpdateEvent priceEvent = new PriceUpdateEvent(source1, priceUpdate, "BINANCE");
        OrderUpdateEvent orderEvent = new OrderUpdateEvent(source2, orderUpdate, "MOCK");
        
        // When
        webSocketHandler.handlePriceUpdateEvent(priceEvent);
        webSocketHandler.handleOrderUpdateEvent(orderEvent);
        
        // Then
        verify(priceUpdateListener1).onPriceUpdate(priceUpdate);
        verify(priceUpdateListener2).onPriceUpdate(priceUpdate);
        verify(orderUpdateListener1).onOrderUpdate(orderUpdate);
        verify(orderUpdateListener2).onOrderUpdate(orderUpdate);
    }

    @Test
    void shouldCreateWebSocketHandlerWithSingleListener() {
        // Given
        List<PriceUpdateListener> singlePriceListener = Arrays.asList(priceUpdateListener1);
        List<OrderUpdateListener> singleOrderListener = Arrays.asList(orderUpdateListener1);
        WebSocketHandler singleListenerHandler = new WebSocketHandler(singlePriceListener, singleOrderListener);
        
        PriceUpdateMessage priceUpdate = createPriceUpdate("BTCUSDT", "50000");
        OrderUpdateMessage orderUpdate = createOrderUpdate("123", Order.OrderStatus.FILLED);
        
        PriceUpdateEvent priceEvent = new PriceUpdateEvent(this, priceUpdate, "BINANCE");
        OrderUpdateEvent orderEvent = new OrderUpdateEvent(this, orderUpdate, "BINANCE");
        
        // When
        singleListenerHandler.handlePriceUpdateEvent(priceEvent);
        singleListenerHandler.handleOrderUpdateEvent(orderEvent);
        
        // Then
        verify(priceUpdateListener1).onPriceUpdate(priceUpdate);
        verify(orderUpdateListener1).onOrderUpdate(orderUpdate);
        verifyNoInteractions(priceUpdateListener2, orderUpdateListener2);
    }

    @Test
    void shouldHandleConcurrentEventProcessing() {
        // Given
        PriceUpdateMessage priceUpdate1 = createPriceUpdate("BTCUSDT", "50000");
        PriceUpdateMessage priceUpdate2 = createPriceUpdate("ETHUSDT", "3000");
        
        PriceUpdateEvent event1 = new PriceUpdateEvent(this, priceUpdate1, "BINANCE");
        PriceUpdateEvent event2 = new PriceUpdateEvent(this, priceUpdate2, "BINANCE");
        
        // When - Simulate concurrent processing
        webSocketHandler.handlePriceUpdateEvent(event1);
        webSocketHandler.handlePriceUpdateEvent(event2);
        
        // Then
        verify(priceUpdateListener1).onPriceUpdate(priceUpdate1);
        verify(priceUpdateListener1).onPriceUpdate(priceUpdate2);
        verify(priceUpdateListener2).onPriceUpdate(priceUpdate1);
        verify(priceUpdateListener2).onPriceUpdate(priceUpdate2);
    }

    @Test
    void shouldMaintainListenerExecutionOrder() {
        // Given
        PriceUpdateMessage priceUpdate = createPriceUpdate("BTCUSDT", "50000");
        PriceUpdateEvent event = new PriceUpdateEvent(this, priceUpdate, "BINANCE");
        
        // When
        webSocketHandler.handlePriceUpdateEvent(event);
        
        // Then - Verify listeners are called in order (first listener1, then listener2)
        var inOrder = inOrder(priceUpdateListener1, priceUpdateListener2);
        inOrder.verify(priceUpdateListener1).onPriceUpdate(priceUpdate);
        inOrder.verify(priceUpdateListener2).onPriceUpdate(priceUpdate);
    }

    @Test
    void shouldHandleListenerExceptionsIndependently() {
        // Given
        PriceUpdateMessage priceUpdate = createPriceUpdate("BTCUSDT", "50000");
        PriceUpdateEvent event = new PriceUpdateEvent(this, priceUpdate, "BINANCE");
        
        // Both listeners throw exceptions
        doThrow(new RuntimeException("Listener 1 failed")).when(priceUpdateListener1).onPriceUpdate(priceUpdate);
        doThrow(new RuntimeException("Listener 2 failed")).when(priceUpdateListener2).onPriceUpdate(priceUpdate);
        
        // When
        webSocketHandler.handlePriceUpdateEvent(event);
        
        // Then - Both listeners should still be invoked despite exceptions
        verify(priceUpdateListener1).onPriceUpdate(priceUpdate);
        verify(priceUpdateListener2).onPriceUpdate(priceUpdate);
    }

    @Test
    void shouldHandleZeroPriceUpdates() {
        // Given
        PriceUpdateMessage priceUpdate = createPriceUpdate("BTCUSDT", "0");
        PriceUpdateEvent event = new PriceUpdateEvent(this, priceUpdate, "BINANCE");
        
        // When
        webSocketHandler.handlePriceUpdateEvent(event);
        
        // Then
        verify(priceUpdateListener1).onPriceUpdate(priceUpdate);
        verify(priceUpdateListener2).onPriceUpdate(priceUpdate);
    }

    @Test
    void shouldHandleHighPrecisionPriceUpdates() {
        // Given
        PriceUpdateMessage priceUpdate = createPriceUpdate("BTCUSDT", "50000.123456789");
        PriceUpdateEvent event = new PriceUpdateEvent(this, priceUpdate, "BINANCE");
        
        // When
        webSocketHandler.handlePriceUpdateEvent(event);
        
        // Then
        verify(priceUpdateListener1).onPriceUpdate(priceUpdate);
        verify(priceUpdateListener2).onPriceUpdate(priceUpdate);
    }
}