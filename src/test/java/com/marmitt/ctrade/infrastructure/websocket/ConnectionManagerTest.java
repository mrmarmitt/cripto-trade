package com.marmitt.ctrade.infrastructure.websocket;

import com.marmitt.ctrade.domain.port.ExchangeWebSocketAdapter.ConnectionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.TaskScheduler;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Testes unitários para ConnectionManager.
 * Testa gerenciamento de estado de conexões WebSocket e lógica de reconexão.
 */
@ExtendWith(MockitoExtension.class)
class ConnectionManagerTest {

    @Mock
    private TaskScheduler taskScheduler;
    
    @Mock
    private ConnectionStatsTracker statsTracker;
    
    @Mock
    private WebSocketCircuitBreaker circuitBreaker;
    
    @Mock
    private ReconnectionStrategy reconnectionStrategy;
    
    @Mock
    private ScheduledFuture<?> scheduledFuture;

    private ConnectionManager connectionManager;

    @BeforeEach
    void setUp() {
        connectionManager = new ConnectionManager(
                taskScheduler, 
                statsTracker, 
                circuitBreaker, 
                reconnectionStrategy
        );
    }

    @Test
    void shouldInitializeWithDisconnectedStatus() {
        // Then
        assertThat(connectionManager.getStatus()).isEqualTo(ConnectionStatus.DISCONNECTED);
        assertThat(connectionManager.isConnected()).isFalse();
        assertThat(connectionManager.isConnectingOrConnected()).isFalse();
        assertThat(connectionManager.isOrderUpdatesSubscribed()).isFalse();
        assertThat(connectionManager.getSubscribedPairs()).isEmpty();
    }

    @Test
    void shouldUpdateStatusCorrectly() {
        // When
        connectionManager.updateStatus(ConnectionStatus.CONNECTING);
        
        // Then
        assertThat(connectionManager.getStatus()).isEqualTo(ConnectionStatus.CONNECTING);
        assertThat(connectionManager.isConnected()).isFalse();
        assertThat(connectionManager.isConnectingOrConnected()).isTrue();
        
        // When
        connectionManager.updateStatus(ConnectionStatus.CONNECTED);
        
        // Then
        assertThat(connectionManager.getStatus()).isEqualTo(ConnectionStatus.CONNECTED);
        assertThat(connectionManager.isConnected()).isTrue();
        assertThat(connectionManager.isConnectingOrConnected()).isTrue();
    }

    @Test
    void shouldReturnTrueWhenCircuitBreakerAllowsConnection() {
        // Given
        when(circuitBreaker.canConnect()).thenReturn(true);
        
        // When
        boolean canConnect = connectionManager.canConnect();
        
        // Then
        assertThat(canConnect).isTrue();
        verify(circuitBreaker).canConnect();
    }

    @Test
    void shouldReturnFalseWhenCircuitBreakerBlocksConnection() {
        // Given
        when(circuitBreaker.canConnect()).thenReturn(false);
        
        // When
        boolean canConnect = connectionManager.canConnect();
        
        // Then
        assertThat(canConnect).isFalse();
        verify(circuitBreaker).canConnect();
    }

    @Test
    void shouldAddSubscriptionForTradingPair() {
        // When
        connectionManager.addSubscription("BTCUSDT");
        connectionManager.addSubscription("ETHUSDT");
        
        // Then
        Set<String> subscribedPairs = connectionManager.getSubscribedPairs();
        assertThat(subscribedPairs).containsExactlyInAnyOrder("BTCUSDT", "ETHUSDT");
    }

    @Test
    void shouldNotAllowDuplicateSubscriptions() {
        // When
        connectionManager.addSubscription("BTCUSDT");
        connectionManager.addSubscription("BTCUSDT");
        
        // Then
        Set<String> subscribedPairs = connectionManager.getSubscribedPairs();
        assertThat(subscribedPairs).containsExactly("BTCUSDT");
    }

    @Test
    void shouldReturnImmutableCopyOfSubscribedPairs() {
        // Given
        connectionManager.addSubscription("BTCUSDT");
        
        // When
        Set<String> subscribedPairs = connectionManager.getSubscribedPairs();
        
        // Then
        assertThat(subscribedPairs).containsExactly("BTCUSDT");
        
        // Verify it's immutable (should throw exception when trying to modify)
        try {
            subscribedPairs.add("ETHUSDT");
            // If we reach here, the set is mutable (test should fail)
            assertThat(false).as("Expected UnsupportedOperationException").isTrue();
        } catch (UnsupportedOperationException e) {
            // Expected behavior - set is immutable
            assertThat(true).isTrue();
        }
    }

    @Test
    void shouldSubscribeToOrderUpdates() {
        // When
        connectionManager.subscribeToOrderUpdates();
        
        // Then
        assertThat(connectionManager.isOrderUpdatesSubscribed()).isTrue();
    }

    @Test
    void shouldResetAllSubscriptions() {
        // Given
        connectionManager.addSubscription("BTCUSDT");
        connectionManager.addSubscription("ETHUSDT");
        connectionManager.subscribeToOrderUpdates();
        
        // When
        connectionManager.resetSubscriptions();
        
        // Then
        assertThat(connectionManager.getSubscribedPairs()).isEmpty();
        assertThat(connectionManager.isOrderUpdatesSubscribed()).isFalse();
    }

    @Test
    void shouldScheduleReconnectionBasedOnStrategy() {
        // Given
        Duration delay = Duration.ofSeconds(5);
        Runnable connectAction = mock(Runnable.class);
        String exchangeName = "BINANCE";
        
        when(reconnectionStrategy.getNextDelay()).thenReturn(delay);
        when(reconnectionStrategy.shouldReconnect()).thenReturn(true);
        when(taskScheduler.schedule(any(Runnable.class), any(Instant.class))).thenAnswer(invocation -> scheduledFuture);
        
        // When
        connectionManager.scheduleReconnectionBasedOnStrategy(connectAction, exchangeName);
        
        // Then
        verify(reconnectionStrategy).getNextDelay();
        verify(reconnectionStrategy).shouldReconnect();
        verify(taskScheduler).schedule(any(Runnable.class), any(Instant.class));
        assertThat(connectionManager.getStatus()).isEqualTo(ConnectionStatus.RECONNECTING);
    }

    @Test
    void shouldNotScheduleReconnectionWhenStrategyDisallows() {
        // Given
        Runnable connectAction = mock(Runnable.class);
        String exchangeName = "BINANCE";
        Duration delay = Duration.ofSeconds(5);
        
        when(reconnectionStrategy.getNextDelay()).thenReturn(delay);
        when(reconnectionStrategy.shouldReconnect()).thenReturn(false);
        
        // When
        connectionManager.scheduleReconnectionBasedOnStrategy(connectAction, exchangeName);
        
        // Then
        verify(reconnectionStrategy).shouldReconnect();
        verify(taskScheduler, never()).schedule(any(Runnable.class), any(Instant.class));
        assertThat(connectionManager.getStatus()).isEqualTo(ConnectionStatus.FAILED);
    }

    @Test
    void shouldCancelReconnectionTask() {
        // Given
        when(scheduledFuture.isCancelled()).thenReturn(false);
        when(reconnectionStrategy.shouldReconnect()).thenReturn(true);
        when(taskScheduler.schedule(any(Runnable.class), any(Instant.class))).thenAnswer(invocation -> scheduledFuture);
        
        Runnable connectAction = mock(Runnable.class);
        connectionManager.scheduleReconnectionBasedOnStrategy(connectAction, "BINANCE");
        
        // When
        connectionManager.cancelReconnectionTask();
        
        // Then
        verify(scheduledFuture).cancel(false);
    }

    @Test
    void shouldNotCancelAlreadyCancelledTask() {
        // Given
        when(scheduledFuture.isCancelled()).thenReturn(true);
        when(reconnectionStrategy.shouldReconnect()).thenReturn(true);
        when(taskScheduler.schedule(any(Runnable.class), any(Instant.class))).thenAnswer(invocation -> scheduledFuture);
        
        Runnable connectAction = mock(Runnable.class);
        connectionManager.scheduleReconnectionBasedOnStrategy(connectAction, "BINANCE");
        
        // When
        connectionManager.cancelReconnectionTask();
        
        // Then
        verify(scheduledFuture, never()).cancel(anyBoolean());
    }

    @Test
    void shouldForceReconnectImmediately() {
        // Given
        Duration delay = Duration.ofSeconds(1);
        Runnable disconnectAction = mock(Runnable.class);
        Runnable connectAction = mock(Runnable.class);
        String exchangeName = "BINANCE";
        
        when(reconnectionStrategy.shouldReconnect()).thenReturn(true);
        when(taskScheduler.schedule(any(Runnable.class), any(Instant.class))).thenAnswer(invocation -> scheduledFuture);
        
        // When
        connectionManager.forceReconnect(delay, disconnectAction, connectAction, exchangeName);
        
        // Then
        verify(disconnectAction).run();
        verify(taskScheduler).schedule(any(Runnable.class), any(Instant.class));
        assertThat(connectionManager.getStatus()).isEqualTo(ConnectionStatus.RECONNECTING);
    }

    @Test
    void shouldExecuteReconnectionActionWhenScheduled() {
        // Given
        Duration delay = Duration.ofSeconds(1);
        Runnable connectAction = mock(Runnable.class);
        String exchangeName = "BINANCE";
        
        when(reconnectionStrategy.shouldReconnect()).thenReturn(true);
        when(taskScheduler.schedule(any(Runnable.class), any(Instant.class))).thenAnswer(invocation -> {
            // Execute the scheduled task immediately for testing
            Runnable task = invocation.getArgument(0);
            task.run();
            return scheduledFuture;
        });
        
        // When
        connectionManager.scheduleReconnectionBasedOnStrategy(connectAction, exchangeName);
        
        // Then
        verify(reconnectionStrategy).recordAttempt();
        verify(statsTracker).recordReconnection();
        verify(connectAction).run();
    }

    @Test
    void shouldHandleExceptionDuringReconnection() {
        // Given
        Runnable connectAction = mock(Runnable.class);
        doThrow(new RuntimeException("Connection failed")).when(connectAction).run();
        String exchangeName = "BINANCE";
        
        when(reconnectionStrategy.shouldReconnect()).thenReturn(true);
        when(taskScheduler.schedule(any(Runnable.class), any(Instant.class))).thenAnswer(invocation -> {
            // Execute the scheduled task immediately for testing
            Runnable task = invocation.getArgument(0);
            task.run();
            return scheduledFuture;
        });
        
        // When
        connectionManager.scheduleReconnectionBasedOnStrategy(connectAction, exchangeName);
        
        // Then
        verify(reconnectionStrategy).recordAttempt();
        verify(statsTracker).recordReconnection();
        verify(connectAction).run();
        assertThat(connectionManager.getStatus()).isEqualTo(ConnectionStatus.FAILED);
    }

    @Test
    void shouldHandleConnectionStatusTransitions() {
        // Test all status transitions
        connectionManager.updateStatus(ConnectionStatus.CONNECTING);
        assertThat(connectionManager.getStatus()).isEqualTo(ConnectionStatus.CONNECTING);
        assertThat(connectionManager.isConnectingOrConnected()).isTrue();
        assertThat(connectionManager.isConnected()).isFalse();
        
        connectionManager.updateStatus(ConnectionStatus.CONNECTED);
        assertThat(connectionManager.getStatus()).isEqualTo(ConnectionStatus.CONNECTED);
        assertThat(connectionManager.isConnectingOrConnected()).isTrue();
        assertThat(connectionManager.isConnected()).isTrue();
        
        connectionManager.updateStatus(ConnectionStatus.DISCONNECTED);
        assertThat(connectionManager.getStatus()).isEqualTo(ConnectionStatus.DISCONNECTED);
        assertThat(connectionManager.isConnectingOrConnected()).isFalse();
        assertThat(connectionManager.isConnected()).isFalse();
        
        connectionManager.updateStatus(ConnectionStatus.FAILED);
        assertThat(connectionManager.getStatus()).isEqualTo(ConnectionStatus.FAILED);
        assertThat(connectionManager.isConnectingOrConnected()).isFalse();
        assertThat(connectionManager.isConnected()).isFalse();
        
        connectionManager.updateStatus(ConnectionStatus.RECONNECTING);
        assertThat(connectionManager.getStatus()).isEqualTo(ConnectionStatus.RECONNECTING);
        assertThat(connectionManager.isConnectingOrConnected()).isFalse();
        assertThat(connectionManager.isConnected()).isFalse();
    }

    @Test
    void shouldHandleConcurrentSubscriptionOperations() {
        // Test concurrent operations (simulating thread safety)
        connectionManager.addSubscription("BTCUSDT");
        connectionManager.subscribeToOrderUpdates();
        connectionManager.addSubscription("ETHUSDT");
        
        Set<String> subscribedPairs = connectionManager.getSubscribedPairs();
        
        assertThat(subscribedPairs).containsExactlyInAnyOrder("BTCUSDT", "ETHUSDT");
        assertThat(connectionManager.isOrderUpdatesSubscribed()).isTrue();
        
        // Reset and verify
        connectionManager.resetSubscriptions();
        assertThat(connectionManager.getSubscribedPairs()).isEmpty();
        assertThat(connectionManager.isOrderUpdatesSubscribed()).isFalse();
    }
}