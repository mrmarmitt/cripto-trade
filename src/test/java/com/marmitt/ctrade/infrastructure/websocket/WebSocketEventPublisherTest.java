package com.marmitt.ctrade.infrastructure.websocket;

import com.marmitt.ctrade.domain.dto.OrderUpdateMessage;
import com.marmitt.ctrade.domain.dto.PriceUpdateMessage;
import com.marmitt.ctrade.domain.entity.Order;
import com.marmitt.ctrade.domain.event.OrderUpdateEvent;
import com.marmitt.ctrade.domain.event.PriceUpdateEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Testes unitários para WebSocketEventPublisher.
 * Testa publicação de eventos de preço e ordem através do Spring Event System.
 */
@ExtendWith(MockitoExtension.class)
class WebSocketEventPublisherTest {

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    private WebSocketEventPublisher webSocketEventPublisher;

    @BeforeEach
    void setUp() {
        webSocketEventPublisher = new WebSocketEventPublisher(applicationEventPublisher);
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
    void shouldPublishPriceUpdateEventCorrectly() {
        // Given
        Object source = "TestAdapter";
        String exchangeName = "BINANCE";
        PriceUpdateMessage priceUpdate = createPriceUpdate("BTCUSDT", "50000.00");

        // When
        webSocketEventPublisher.publishPriceUpdate(source, priceUpdate, exchangeName);

        // Then
        ArgumentCaptor<PriceUpdateEvent> eventCaptor = ArgumentCaptor.forClass(PriceUpdateEvent.class);
        verify(applicationEventPublisher).publishEvent(eventCaptor.capture());

        PriceUpdateEvent capturedEvent = eventCaptor.getValue();
        assertThat(capturedEvent.getSource()).isEqualTo(source);
        assertThat(capturedEvent.getPriceUpdate()).isEqualTo(priceUpdate);
        assertThat(capturedEvent.getExchangeSource()).isEqualTo(exchangeName);
    }

    @Test
    void shouldPublishOrderUpdateEventCorrectly() {
        // Given
        Object source = "TestAdapter";
        String exchangeName = "BINANCE";
        OrderUpdateMessage orderUpdate = createOrderUpdate("123", Order.OrderStatus.FILLED);

        // When
        webSocketEventPublisher.publishOrderUpdate(source, orderUpdate, exchangeName);

        // Then
        ArgumentCaptor<OrderUpdateEvent> eventCaptor = ArgumentCaptor.forClass(OrderUpdateEvent.class);
        verify(applicationEventPublisher).publishEvent(eventCaptor.capture());

        OrderUpdateEvent capturedEvent = eventCaptor.getValue();
        assertThat(capturedEvent.getSource()).isEqualTo(source);
        assertThat(capturedEvent.getOrderUpdate()).isEqualTo(orderUpdate);
        assertThat(capturedEvent.getExchangeSource()).isEqualTo(exchangeName);
    }

    @Test
    void shouldPublishMultiplePriceUpdateEvents() {
        // Given
        Object source = "TestAdapter";
        String exchangeName = "BINANCE";
        
        PriceUpdateMessage priceUpdate1 = createPriceUpdate("BTCUSDT", "50000");
        PriceUpdateMessage priceUpdate2 = createPriceUpdate("ETHUSDT", "3000");
        PriceUpdateMessage priceUpdate3 = createPriceUpdate("ADAUSDT", "2");

        // When
        webSocketEventPublisher.publishPriceUpdate(source, priceUpdate1, exchangeName);
        webSocketEventPublisher.publishPriceUpdate(source, priceUpdate2, exchangeName);
        webSocketEventPublisher.publishPriceUpdate(source, priceUpdate3, exchangeName);

        // Then
        verify(applicationEventPublisher, times(3)).publishEvent(any(PriceUpdateEvent.class));
    }

    @Test
    void shouldPublishMultipleOrderUpdateEvents() {
        // Given
        Object source = "TestAdapter";
        String exchangeName = "BINANCE";
        
        OrderUpdateMessage order1 = createOrderUpdate("123", Order.OrderStatus.FILLED);
        OrderUpdateMessage order2 = createOrderUpdate("124", Order.OrderStatus.PENDING);
        OrderUpdateMessage order3 = createOrderUpdate("125", Order.OrderStatus.CANCELLED);

        // When
        webSocketEventPublisher.publishOrderUpdate(source, order1, exchangeName);
        webSocketEventPublisher.publishOrderUpdate(source, order2, exchangeName);
        webSocketEventPublisher.publishOrderUpdate(source, order3, exchangeName);

        // Then
        verify(applicationEventPublisher, times(3)).publishEvent(any(OrderUpdateEvent.class));
    }

    @Test
    void shouldHandleDifferentSourceObjects() {
        // Given
        Object adapterSource = "BinanceWebSocketAdapter";
        Object serviceSource = "MockWebSocketService";
        String exchangeName = "BINANCE";
        
        PriceUpdateMessage priceUpdate = createPriceUpdate("BTCUSDT", "50000");

        // When
        webSocketEventPublisher.publishPriceUpdate(adapterSource, priceUpdate, exchangeName);
        webSocketEventPublisher.publishPriceUpdate(serviceSource, priceUpdate, exchangeName);

        // Then
        ArgumentCaptor<PriceUpdateEvent> eventCaptor = ArgumentCaptor.forClass(PriceUpdateEvent.class);
        verify(applicationEventPublisher, times(2)).publishEvent(eventCaptor.capture());

        var capturedEvents = eventCaptor.getAllValues();
        assertThat(capturedEvents.get(0).getSource()).isEqualTo(adapterSource);
        assertThat(capturedEvents.get(1).getSource()).isEqualTo(serviceSource);
    }

    @Test
    void shouldHandleDifferentExchangeNames() {
        // Given
        Object source = "TestAdapter";
        PriceUpdateMessage priceUpdate = createPriceUpdate("BTCUSDT", "50000");

        // When
        webSocketEventPublisher.publishPriceUpdate(source, priceUpdate, "BINANCE");
        webSocketEventPublisher.publishPriceUpdate(source, priceUpdate, "MOCK");
        webSocketEventPublisher.publishPriceUpdate(source, priceUpdate, "COINBASE");

        // Then
        ArgumentCaptor<PriceUpdateEvent> eventCaptor = ArgumentCaptor.forClass(PriceUpdateEvent.class);
        verify(applicationEventPublisher, times(3)).publishEvent(eventCaptor.capture());

        var capturedEvents = eventCaptor.getAllValues();
        assertThat(capturedEvents.get(0).getExchangeSource()).isEqualTo("BINANCE");
        assertThat(capturedEvents.get(1).getExchangeSource()).isEqualTo("MOCK");
        assertThat(capturedEvents.get(2).getExchangeSource()).isEqualTo("COINBASE");
    }

    @Test
    void shouldHandleNullPriceUpdateMessage() {
        // Given
        Object source = "TestAdapter";
        String exchangeName = "BINANCE";

        // When
        webSocketEventPublisher.publishPriceUpdate(source, null, exchangeName);

        // Then
        ArgumentCaptor<PriceUpdateEvent> eventCaptor = ArgumentCaptor.forClass(PriceUpdateEvent.class);
        verify(applicationEventPublisher).publishEvent(eventCaptor.capture());

        PriceUpdateEvent capturedEvent = eventCaptor.getValue();
        assertThat(capturedEvent.getPriceUpdate()).isNull();
        assertThat(capturedEvent.getSource()).isEqualTo(source);
    }

    @Test
    void shouldHandleNullOrderUpdateMessage() {
        // Given
        Object source = "TestAdapter";
        String exchangeName = "BINANCE";

        // When
        webSocketEventPublisher.publishOrderUpdate(source, null, exchangeName);

        // Then
        ArgumentCaptor<OrderUpdateEvent> eventCaptor = ArgumentCaptor.forClass(OrderUpdateEvent.class);
        verify(applicationEventPublisher).publishEvent(eventCaptor.capture());

        OrderUpdateEvent capturedEvent = eventCaptor.getValue();
        assertThat(capturedEvent.getOrderUpdate()).isNull();
        assertThat(capturedEvent.getSource()).isEqualTo(source);
    }

    @Test
    void shouldHandleNullSource() {
        // Given
        String exchangeName = "BINANCE";
        PriceUpdateMessage priceUpdate = createPriceUpdate("BTCUSDT", "50000");

        // When
        webSocketEventPublisher.publishPriceUpdate(null, priceUpdate, exchangeName);

        // Then
        ArgumentCaptor<PriceUpdateEvent> eventCaptor = ArgumentCaptor.forClass(PriceUpdateEvent.class);
        verify(applicationEventPublisher).publishEvent(eventCaptor.capture());

        PriceUpdateEvent capturedEvent = eventCaptor.getValue();
        assertThat(capturedEvent.getSource()).isEqualTo("UNKNOWN_SOURCE");
        assertThat(capturedEvent.getPriceUpdate()).isEqualTo(priceUpdate);
    }

    @Test
    void shouldHandleNullExchangeName() {
        // Given
        Object source = "TestAdapter";
        PriceUpdateMessage priceUpdate = createPriceUpdate("BTCUSDT", "50000");

        // When
        webSocketEventPublisher.publishPriceUpdate(source, priceUpdate, null);

        // Then
        ArgumentCaptor<PriceUpdateEvent> eventCaptor = ArgumentCaptor.forClass(PriceUpdateEvent.class);
        verify(applicationEventPublisher).publishEvent(eventCaptor.capture());

        PriceUpdateEvent capturedEvent = eventCaptor.getValue();
        assertThat(capturedEvent.getExchangeSource()).isNull();
        assertThat(capturedEvent.getPriceUpdate()).isEqualTo(priceUpdate);
    }

    @Test
    void shouldPropagateExceptionsFromApplicationEventPublisher() {
        // Given
        Object source = "TestAdapter";
        String exchangeName = "BINANCE";
        PriceUpdateMessage priceUpdate = createPriceUpdate("BTCUSDT", "50000");
        
        RuntimeException exception = new RuntimeException("Event publishing failed");
        doThrow(exception).when(applicationEventPublisher).publishEvent(any(PriceUpdateEvent.class));

        // When/Then
        try {
            webSocketEventPublisher.publishPriceUpdate(source, priceUpdate, exchangeName);
        } catch (RuntimeException e) {
            assertThat(e).isSameAs(exception);
            verify(applicationEventPublisher).publishEvent(any(PriceUpdateEvent.class));
        }
    }

    @Test
    void shouldCreateCorrectPriceUpdateEventUsingStaticMethod() {
        // Given
        Object source = "TestAdapter";
        String exchangeName = "BINANCE";
        PriceUpdateMessage priceUpdate = createPriceUpdate("BTCUSDT", "50000");

        // When
        webSocketEventPublisher.publishPriceUpdate(source, priceUpdate, exchangeName);

        // Then
        ArgumentCaptor<PriceUpdateEvent> eventCaptor = ArgumentCaptor.forClass(PriceUpdateEvent.class);
        verify(applicationEventPublisher).publishEvent(eventCaptor.capture());

        PriceUpdateEvent capturedEvent = eventCaptor.getValue();
        
        // Verify the event was created using the static factory method
        PriceUpdateEvent expectedEvent = PriceUpdateEvent.of(source, priceUpdate, exchangeName);
        assertThat(capturedEvent.getSource()).isEqualTo(expectedEvent.getSource());
        assertThat(capturedEvent.getPriceUpdate()).isEqualTo(expectedEvent.getPriceUpdate());
        assertThat(capturedEvent.getSource()).isEqualTo(expectedEvent.getSource());
    }

    @Test
    void shouldCreateCorrectOrderUpdateEventUsingStaticMethod() {
        // Given
        Object source = "TestAdapter";
        String exchangeName = "BINANCE";
        OrderUpdateMessage orderUpdate = createOrderUpdate("123", Order.OrderStatus.FILLED);

        // When
        webSocketEventPublisher.publishOrderUpdate(source, orderUpdate, exchangeName);

        // Then
        ArgumentCaptor<OrderUpdateEvent> eventCaptor = ArgumentCaptor.forClass(OrderUpdateEvent.class);
        verify(applicationEventPublisher).publishEvent(eventCaptor.capture());

        OrderUpdateEvent capturedEvent = eventCaptor.getValue();
        
        // Verify the event was created using the static factory method
        OrderUpdateEvent expectedEvent = OrderUpdateEvent.of(source, orderUpdate, exchangeName);
        assertThat(capturedEvent.getSource()).isEqualTo(expectedEvent.getSource());
        assertThat(capturedEvent.getOrderUpdate()).isEqualTo(expectedEvent.getOrderUpdate());
        assertThat(capturedEvent.getSource()).isEqualTo(expectedEvent.getSource());
    }

    @Test
    void shouldHandleMixedEventTypes() {
        // Given
        Object source = "TestAdapter";
        String exchangeName = "BINANCE";
        
        PriceUpdateMessage priceUpdate = createPriceUpdate("BTCUSDT", "50000");
        OrderUpdateMessage orderUpdate = createOrderUpdate("123", Order.OrderStatus.FILLED);

        // When
        webSocketEventPublisher.publishPriceUpdate(source, priceUpdate, exchangeName);
        webSocketEventPublisher.publishOrderUpdate(source, orderUpdate, exchangeName);
        webSocketEventPublisher.publishPriceUpdate(source, priceUpdate, exchangeName);

        // Then
        verify(applicationEventPublisher, times(2)).publishEvent(any(PriceUpdateEvent.class));
        verify(applicationEventPublisher, times(1)).publishEvent(any(OrderUpdateEvent.class));
        verify(applicationEventPublisher, times(3)).publishEvent(any());
    }

    @Test
    void shouldHandleConcurrentEventPublishing() throws InterruptedException {
        // Given
        Object source = "TestAdapter";
        String exchangeName = "BINANCE";
        int numberOfThreads = 10;
        int eventsPerThread = 100;
        
        Thread[] threads = new Thread[numberOfThreads];

        // When
        for (int i = 0; i < numberOfThreads; i++) {
            final int threadIndex = i;
            threads[i] = new Thread(() -> {
                for (int j = 0; j < eventsPerThread; j++) {
                    PriceUpdateMessage priceUpdate = createPriceUpdate(
                        "PAIR" + threadIndex + "_" + j, 
                        String.valueOf(j)
                    );
                    webSocketEventPublisher.publishPriceUpdate(source, priceUpdate, exchangeName);
                }
            });
            threads[i].start();
        }

        // Wait for all threads to complete
        for (Thread thread : threads) {
            thread.join();
        }

        // Then
        verify(applicationEventPublisher, times(numberOfThreads * eventsPerThread))
            .publishEvent(any(PriceUpdateEvent.class));
    }

    @Test
    void shouldHandleEmptyStringsGracefully() {
        // Given
        Object source = "";
        String exchangeName = "";
        PriceUpdateMessage priceUpdate = createPriceUpdate("", "0");

        // When
        webSocketEventPublisher.publishPriceUpdate(source, priceUpdate, exchangeName);

        // Then
        ArgumentCaptor<PriceUpdateEvent> eventCaptor = ArgumentCaptor.forClass(PriceUpdateEvent.class);
        verify(applicationEventPublisher).publishEvent(eventCaptor.capture());

        PriceUpdateEvent capturedEvent = eventCaptor.getValue();
        assertThat(capturedEvent.getSource()).isEqualTo(source);
        assertThat(capturedEvent.getPriceUpdate()).isEqualTo(priceUpdate);
        assertThat(capturedEvent.getExchangeSource()).isEqualTo(exchangeName);
    }

    @Test
    void shouldVerifyNoUnexpectedInteractionsWithEventPublisher() {
        // Given
        Object source = "TestAdapter";
        String exchangeName = "BINANCE";
        PriceUpdateMessage priceUpdate = createPriceUpdate("BTCUSDT", "50000");

        // When
        webSocketEventPublisher.publishPriceUpdate(source, priceUpdate, exchangeName);

        // Then
        verify(applicationEventPublisher).publishEvent(any(PriceUpdateEvent.class));
        verifyNoMoreInteractions(applicationEventPublisher);
    }

    @Test
    void shouldHandleComplexSourceObjects() {
        // Given
        Object complexSource = new Object() {
            @Override
            public String toString() {
                return "ComplexSourceObject";
            }
        };
        String exchangeName = "BINANCE";
        PriceUpdateMessage priceUpdate = createPriceUpdate("BTCUSDT", "50000");

        // When
        webSocketEventPublisher.publishPriceUpdate(complexSource, priceUpdate, exchangeName);

        // Then
        ArgumentCaptor<PriceUpdateEvent> eventCaptor = ArgumentCaptor.forClass(PriceUpdateEvent.class);
        verify(applicationEventPublisher).publishEvent(eventCaptor.capture());

        PriceUpdateEvent capturedEvent = eventCaptor.getValue();
        assertThat(capturedEvent.getSource()).isEqualTo(complexSource);
    }
}