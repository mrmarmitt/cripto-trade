package com.marmitt.ctrade.infrastructure.exchange.binance;

import com.marmitt.ctrade.application.service.WebSocketService;
import com.marmitt.ctrade.domain.port.ExchangeWebSocketAdapter;
import com.marmitt.ctrade.infrastructure.exchange.binance.parser.BinanceStreamParser;
import com.marmitt.ctrade.infrastructure.websocket.ReconnectionStrategy;
import com.marmitt.ctrade.infrastructure.websocket.WebSocketCircuitBreaker;
import okhttp3.Response;
import okhttp3.WebSocket;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BinanceWebSocketListenerTest {
    
    @Mock
    private WebSocketService webSocketService;
    
    @Mock
    private ReconnectionStrategy reconnectionStrategy;
    
    @Mock
    private WebSocketCircuitBreaker circuitBreaker;
    
    @Mock
    private Consumer<ExchangeWebSocketAdapter.ConnectionStatus> statusUpdater;
    
    @Mock
    private Consumer<LocalDateTime> lastConnectedAtUpdater;
    
    @Mock
    private Consumer<LocalDateTime> lastMessageAtUpdater;
    
    @Mock
    private Runnable scheduleReconnectionCallback;
    
    @Mock
    private WebSocket webSocket;
    
    @Mock
    private Response response;
    
    @Mock
    private BinanceStreamParser streamParser;
    
    private Set<String> subscribedPairs;
    private AtomicLong totalMessagesReceived;
    private AtomicLong totalErrors;
    private BinanceWebSocketListener listener;
    
    @BeforeEach
    void setUp() {
        subscribedPairs = ConcurrentHashMap.newKeySet();
        totalMessagesReceived = new AtomicLong(0);
        totalErrors = new AtomicLong(0);
        
        listener = new BinanceWebSocketListener(
                streamParser,
                subscribedPairs,
                reconnectionStrategy,
                circuitBreaker,
                totalMessagesReceived,
                totalErrors,
                statusUpdater,
                lastConnectedAtUpdater,
                lastMessageAtUpdater,
                scheduleReconnectionCallback,
                null,
                null
        );
    }
    
    @Test
    void shouldHandleOnOpenCorrectly() {
        // When
        listener.onOpen(webSocket, response);
        
        // Then
        verify(statusUpdater).accept(ExchangeWebSocketAdapter.ConnectionStatus.CONNECTED);
        verify(lastConnectedAtUpdater).accept(any(LocalDateTime.class));
        verify(reconnectionStrategy).reset();
        verify(circuitBreaker).recordSuccess();
    }
    
    @Test
    void shouldDelegateMessageParsingToStreamParser() {
        // Given
        String testMessage = "test message";
        
        // When
        listener.onMessage(webSocket, testMessage);
        
        // Then
        verify(lastMessageAtUpdater).accept(any(LocalDateTime.class));
        assertThat(totalMessagesReceived.get()).isEqualTo(1);
        verify(streamParser).parseMessage(testMessage);
    }
    
    @Test
    void shouldHandleParsingErrors() {
        // Given
        String invalidMessage = "invalid json";
        doThrow(new RuntimeException("Parse error")).when(streamParser).parseMessage(invalidMessage);
        
        // When
        listener.onMessage(webSocket, invalidMessage);
        
        // Then
        assertThat(totalErrors.get()).isEqualTo(1);
        verify(streamParser).parseMessage(invalidMessage);
    }
    
    @Test
    void shouldHandleOnClosingCorrectly() {
        // When
        listener.onClosing(webSocket, 1000, "Normal closure");
        
        // Then - Just verify it doesn't throw
    }
    
    @Test
    void shouldHandleOnClosedWithNormalClosure() {
        // When
        listener.onClosed(webSocket, 1000, "Normal closure");
        
        // Then
        verify(statusUpdater).accept(ExchangeWebSocketAdapter.ConnectionStatus.DISCONNECTED);
        verify(scheduleReconnectionCallback, never()).run(); // No reconnection for normal closure
    }
    
    @Test
    void shouldHandleOnClosedWithAbnormalClosure() {
        // When
        listener.onClosed(webSocket, 1006, "Abnormal closure");
        
        // Then
        verify(statusUpdater).accept(ExchangeWebSocketAdapter.ConnectionStatus.DISCONNECTED);
        verify(circuitBreaker).recordFailure();
        verify(scheduleReconnectionCallback).run(); // Should trigger reconnection
    }
    
    @Test
    void shouldHandleOnFailureCorrectly() {
        // Given
        Throwable throwable = new RuntimeException("Connection failed");
        
        // When
        listener.onFailure(webSocket, throwable, response);
        
        // Then
        verify(statusUpdater).accept(ExchangeWebSocketAdapter.ConnectionStatus.FAILED);
        assertThat(totalErrors.get()).isEqualTo(1);
        verify(circuitBreaker).recordFailure();
        verify(scheduleReconnectionCallback).run();
    }
}