package com.marmitt.ctrade.infrastructure.websocket;

import com.marmitt.ctrade.domain.dto.OrderUpdateMessage;
import com.marmitt.ctrade.domain.dto.PriceUpdateMessage;
import com.marmitt.ctrade.domain.port.ExchangeWebSocketAdapter.ConnectionStatus;
import com.marmitt.ctrade.domain.entity.Order;
import com.marmitt.ctrade.domain.port.ExchangeWebSocketAdapter.ConnectionStats;
import com.marmitt.ctrade.infrastructure.config.WebSocketProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

/**
 * Testes unitários para AbstractWebSocketAdapter.
 * Testa a orquestração entre serviços e template methods.
 */
@ExtendWith(MockitoExtension.class)
class AbstractWebSocketAdapterTest {

    @Mock
    private WebSocketEventPublisher eventPublisher;
    
    @Mock
    private ConnectionManager connectionManager;
    
    @Mock
    private ConnectionStatsTracker statsTracker;
    
    @Mock
    private WebSocketProperties properties;
    
    @Mock
    private ConnectionStats connectionStats;

    private TestWebSocketAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new TestWebSocketAdapter(eventPublisher, connectionManager, statsTracker, properties);
        lenient().when(properties.getUrl()).thenReturn("ws://test.exchange.com");
    }

    @Test
    void shouldReturnConnectionStatusFromManager() {
        // Given
        when(connectionManager.getStatus()).thenReturn(ConnectionStatus.CONNECTED);
        
        // When
        ConnectionStatus status = adapter.getConnectionStatus();
        
        // Then
        assertThat(status).isEqualTo(ConnectionStatus.CONNECTED);
        verify(connectionManager).getStatus();
    }

    @Test
    void shouldReturnSubscribedPairsFromManager() {
        // Given
        Set<String> pairs = Set.of("BTCUSDT", "ETHUSDT");
        when(connectionManager.getSubscribedPairs()).thenReturn(pairs);
        
        // When
        Set<String> result = adapter.getSubscribedPairs();
        
        // Then
        assertThat(result).isEqualTo(pairs);
        verify(connectionManager).getSubscribedPairs();
    }

    @Test
    void shouldReturnOrderUpdatesSubscriptionStatusFromManager() {
        // Given
        when(connectionManager.isOrderUpdatesSubscribed()).thenReturn(true);
        
        // When
        boolean isSubscribed = adapter.isOrderUpdatesSubscribed();
        
        // Then
        assertThat(isSubscribed).isTrue();
        verify(connectionManager).isOrderUpdatesSubscribed();
    }

    @Test
    void shouldReturnConnectionStatsFromTracker() {
        // Given
        when(statsTracker.getStats()).thenReturn(connectionStats);
        
        // When
        ConnectionStats result = adapter.getConnectionStats();
        
        // Then
        assertThat(result).isEqualTo(connectionStats);
        verify(statsTracker).getStats();
    }

    @Test
    void shouldReturnConnectedStatusFromManager() {
        // Given
        when(connectionManager.isConnected()).thenReturn(true);
        
        // When
        boolean isConnected = adapter.isConnected();
        
        // Then
        assertThat(isConnected).isTrue();
        verify(connectionManager).isConnected();
    }

    @Test
    void shouldConnectWhenCircuitBreakerAllows() {
        // Given
        when(connectionManager.canConnect()).thenReturn(true);
        when(connectionManager.isConnectingOrConnected()).thenReturn(false);
        
        // When
        adapter.connect();
        
        // Then
        verify(connectionManager).canConnect();
        verify(connectionManager).isConnectingOrConnected();
        verify(connectionManager).updateStatus(ConnectionStatus.CONNECTING);
        verify(statsTracker).recordConnection();
        assertThat(adapter.doConnectCalled).isTrue();
    }

    @Test
    void shouldNotConnectWhenCircuitBreakerBlocks() {
        // Given
        when(connectionManager.canConnect()).thenReturn(false);
        
        // When
        adapter.connect();
        
        // Then
        verify(connectionManager).canConnect();
        verify(connectionManager, never()).updateStatus(any());
        verify(statsTracker, never()).recordConnection();
        assertThat(adapter.doConnectCalled).isFalse();
    }

    @Test
    void shouldNotConnectWhenAlreadyConnectingOrConnected() {
        // Given
        when(connectionManager.canConnect()).thenReturn(true);
        when(connectionManager.isConnectingOrConnected()).thenReturn(true);
        
        // When
        adapter.connect();
        
        // Then
        verify(connectionManager).canConnect();
        verify(connectionManager).isConnectingOrConnected();
        verify(connectionManager, never()).updateStatus(any());
        verify(statsTracker, never()).recordConnection();
        assertThat(adapter.doConnectCalled).isFalse();
    }

    @Test
    void shouldDisconnectAndResetState() {
        // When
        adapter.disconnect();
        
        // Then
        verify(connectionManager).updateStatus(ConnectionStatus.DISCONNECTED);
        verify(connectionManager).resetSubscriptions();
        verify(connectionManager).cancelReconnectionTask();
        assertThat(adapter.doDisconnectCalled).isTrue();
    }

    @Test
    void shouldSubscribeToPriceWhenConnected() {
        // Given
        String tradingPair = "BTCUSDT";
        when(connectionManager.isConnected()).thenReturn(true);
        
        // When
        adapter.subscribeToPrice(tradingPair);
        
        // Then
        verify(connectionManager).isConnected();
        verify(connectionManager).addSubscription(tradingPair);
        assertThat(adapter.lastSubscribedPair).isEqualTo(tradingPair);
    }

    @Test
    void shouldNotSubscribeToPriceWhenNotConnected() {
        // Given
        String tradingPair = "BTCUSDT";
        when(connectionManager.isConnected()).thenReturn(false);
        
        // When
        adapter.subscribeToPrice(tradingPair);
        
        // Then
        verify(connectionManager).isConnected();
        verify(connectionManager, never()).addSubscription(any());
        assertThat(adapter.lastSubscribedPair).isNull();
    }

    @Test
    void shouldSubscribeToOrderUpdatesWhenConnected() {
        // Given
        when(connectionManager.isConnected()).thenReturn(true);
        
        // When
        adapter.subscribeToOrderUpdates();
        
        // Then
        verify(connectionManager).isConnected();
        verify(connectionManager).subscribeToOrderUpdates();
        assertThat(adapter.doSubscribeToOrderUpdatesCalled).isTrue();
    }

    @Test
    void shouldNotSubscribeToOrderUpdatesWhenNotConnected() {
        // Given
        when(connectionManager.isConnected()).thenReturn(false);
        
        // When
        adapter.subscribeToOrderUpdates();
        
        // Then
        verify(connectionManager).isConnected();
        verify(connectionManager, never()).subscribeToOrderUpdates();
        assertThat(adapter.doSubscribeToOrderUpdatesCalled).isFalse();
    }

    @Test
    void shouldForceReconnectWithCorrectParameters() {
        // When
        adapter.forceReconnect();
        
        // Then
        verify(connectionManager).forceReconnect(
            eq(Duration.ofSeconds(1)),
            any(Runnable.class),
            any(Runnable.class),
            eq("TEST_EXCHANGE")
        );
    }

    @Test
    void shouldScheduleReconnectionUsingManager() {
        // When
        adapter.scheduleReconnection();
        
        // Then
        verify(connectionManager).scheduleReconnectionBasedOnStrategy(
            any(Runnable.class),
            eq("TEST_EXCHANGE")
        );
    }

    @Test
    void shouldUpdateConnectionStatusThroughManager() {
        // When
        adapter.updateConnectionStatus(ConnectionStatus.FAILED);
        
        // Then
        verify(connectionManager).updateStatus(ConnectionStatus.FAILED);
    }

    @Test
    void shouldRecordConnectionThroughStatsTracker() {
        // When
        adapter.recordConnection();
        
        // Then
        verify(statsTracker).recordConnection();
    }

    @Test
    void shouldRecordErrorThroughStatsTracker() {
        // When
        adapter.recordError();
        
        // Then
        verify(statsTracker).recordError();
    }

    @Test
    void shouldProcessPriceUpdateCorrectly() {
        // Given
        PriceUpdateMessage priceUpdate = new PriceUpdateMessage();
        priceUpdate.setTradingPair("BTCUSDT");
        priceUpdate.setPrice(new BigDecimal("50000.00"));
        priceUpdate.setTimestamp(LocalDateTime.now());
        
        // When
        adapter.onPriceUpdate(priceUpdate);
        
        // Then
        verify(statsTracker).recordMessageReceived();
        verify(eventPublisher).publishPriceUpdate(adapter, priceUpdate, "TEST_EXCHANGE");
    }

    @Test
    void shouldProcessOrderUpdateCorrectly() {
        // Given
        OrderUpdateMessage orderUpdate = new OrderUpdateMessage();
        orderUpdate.setOrderId("123");
        orderUpdate.setStatus(Order.OrderStatus.FILLED);
        orderUpdate.setReason("Trade executed");
        orderUpdate.setTimestamp(LocalDateTime.now());
        
        // When
        adapter.onOrderUpdate(orderUpdate);
        
        // Then
        verify(statsTracker).recordMessageReceived();
        verify(eventPublisher).publishOrderUpdate(adapter, orderUpdate, "TEST_EXCHANGE");
    }

    @Test
    void shouldReturnCorrectExchangeName() {
        // When
        String exchangeName = adapter.getExchangeName();
        
        // Then
        assertThat(exchangeName).isEqualTo("TEST_EXCHANGE");
    }

    @Test
    void shouldCallDoConnectWhenForceReconnectRunsConnectAction() {
        // Given
        doAnswer(invocation -> {
            // Execute the connect action (3rd argument)
            Runnable connectAction = invocation.getArgument(2);
            connectAction.run();
            return null;
        }).when(connectionManager).forceReconnect(any(), any(), any(), any());
        
        // When
        adapter.forceReconnect();
        
        // Then
        assertThat(adapter.doConnectCalled).isTrue();
    }

    @Test
    void shouldCallDoDisconnectWhenForceReconnectRunsDisconnectAction() {
        // Given
        doAnswer(invocation -> {
            // Execute the disconnect action (2nd argument)
            Runnable disconnectAction = invocation.getArgument(1);
            disconnectAction.run();
            return null;
        }).when(connectionManager).forceReconnect(any(), any(), any(), any());
        
        // When
        adapter.forceReconnect();
        
        // Then
        assertThat(adapter.doDisconnectCalled).isTrue();
    }

    @Test
    void shouldCallDoConnectWhenScheduleReconnectionRunsAction() {
        // Given
        doAnswer(invocation -> {
            // Execute the connect action (1st argument)
            Runnable connectAction = invocation.getArgument(0);
            connectAction.run();
            return null;
        }).when(connectionManager).scheduleReconnectionBasedOnStrategy(any(), any());
        
        // When
        adapter.scheduleReconnection();
        
        // Then
        assertThat(adapter.doConnectCalled).isTrue();
    }

    @Test
    void shouldHandleNullPriceUpdateGracefully() {
        // When
        adapter.onPriceUpdate(null);
        
        // Then
        verify(statsTracker).recordMessageReceived();
        verify(eventPublisher).publishPriceUpdate(adapter, null, "TEST_EXCHANGE");
    }

    @Test
    void shouldHandleNullOrderUpdateGracefully() {
        // When
        adapter.onOrderUpdate(null);
        
        // Then
        verify(statsTracker).recordMessageReceived();
        verify(eventPublisher).publishOrderUpdate(adapter, null, "TEST_EXCHANGE");
    }

    /**
     * Concrete test implementation of AbstractWebSocketAdapter for testing.
     */
    private static class TestWebSocketAdapter extends AbstractWebSocketAdapter {
        
        boolean doConnectCalled = false;
        boolean doDisconnectCalled = false;
        boolean doSubscribeToOrderUpdatesCalled = false;
        String lastSubscribedPair = null;

        protected TestWebSocketAdapter(WebSocketEventPublisher eventPublisher,
                                       ConnectionManager connectionManager,
                                       ConnectionStatsTracker statsTracker,
                                       WebSocketProperties properties) {
            super(eventPublisher, connectionManager, statsTracker, properties);
        }

        @Override
        public String getExchangeName() {
            return "TEST_EXCHANGE";
        }

        @Override
        protected void doConnect() {
            doConnectCalled = true;
        }

        @Override
        protected void doDisconnect() {
            doDisconnectCalled = true;
        }

        @Override
        protected void doSubscribeToPrice(String tradingPair) {
            lastSubscribedPair = tradingPair;
        }

        @Override
        protected void doSubscribeToOrderUpdates() {
            doSubscribeToOrderUpdatesCalled = true;
        }
    }
}