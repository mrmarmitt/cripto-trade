package com.marmitt.ctrade.infrastructure.exchange.binance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marmitt.ctrade.application.service.WebSocketService;
import com.marmitt.ctrade.domain.dto.OrderUpdateMessage;
import com.marmitt.ctrade.domain.dto.PriceUpdateMessage;
import com.marmitt.ctrade.domain.port.ExchangeWebSocketAdapter;
import com.marmitt.ctrade.domain.strategy.StreamProcessingStrategy;
import com.marmitt.ctrade.infrastructure.websocket.ReconnectionStrategy;
import com.marmitt.ctrade.infrastructure.websocket.WebSocketCircuitBreaker;
import com.marmitt.ctrade.infrastructure.websocket.WebSocketConnectionHandler;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BinanceWebSocketListenerTest {
    
    @Mock
    private WebSocketService webSocketService;
    
    @Mock
    private WebSocketConnectionHandler connectionHandler;
    
    @Mock
    private ReconnectionStrategy reconnectionStrategy;
    
    @Mock
    private WebSocketCircuitBreaker circuitBreaker;
    
    @Mock
    private Consumer<PriceUpdateMessage> onPriceUpdate;
    
    @Mock
    private Consumer<OrderUpdateMessage> onOrderUpdate;
    
    @Mock
    private Runnable scheduleReconnectionCallback;
    
    @Mock
    private WebSocket webSocket;
    
    @Mock
    private Response response;
    
    @Mock
    private StreamProcessingStrategy streamProcessingStrategy;
    
    private Set<String> subscribedPairs;
    private AtomicLong totalMessagesReceived;
    private AtomicLong totalErrors;
    private BinanceWebSocketListener listener;
    
    @BeforeEach
    void setUp() {
        subscribedPairs = ConcurrentHashMap.newKeySet();
        totalMessagesReceived = new AtomicLong(0);
        totalErrors = new AtomicLong(0);
        
        // Usa construtor para testes (com strategy mockada)
        listener = new BinanceWebSocketListener(
                connectionHandler,
                streamProcessingStrategy,
                scheduleReconnectionCallback,
                onPriceUpdate,
                onOrderUpdate
        );
    }
    
    @Test
    void shouldHandleOnOpenCorrectly() {
        // When
        listener.onOpen(webSocket, response);
        
        // Then
        verify(connectionHandler).handleConnectionOpened(eq("BINANCE"));
    }
    
    @Test
    void shouldDelegateMessageParsingToStreamParser() {
        // Given
        String testMessage = "test message";
        
        // When
        listener.onMessage(webSocket, testMessage);
        
        // Then
        verify(connectionHandler).handleMessageReceived();
        verify(streamProcessingStrategy).processPriceUpdate(testMessage);
        verify(streamProcessingStrategy).processOrderUpdate(testMessage);
    }
    
    @Test
    void shouldHandleParsingErrors() {
        // Given
        String invalidMessage = "invalid json";
        RuntimeException parseError = new RuntimeException("Parse error");
        doThrow(parseError).when(streamProcessingStrategy).processPriceUpdate(invalidMessage);
        
        // When
        listener.onMessage(webSocket, invalidMessage);
        
        // Then
        verify(connectionHandler).handleProcessingError(eq("BINANCE"), eq(parseError));
        verify(streamProcessingStrategy).processPriceUpdate(invalidMessage);
    }
    
    @Test
    void shouldHandleOnClosingCorrectly() {
        // When
        listener.onClosing(webSocket, 1000, "Normal closure");
        
        // Then
        verify(connectionHandler).handleConnectionClosing(eq("BINANCE"), eq(1000), eq("Normal closure"));
    }
    
    @Test
    void shouldHandleOnClosedWithNormalClosure() {
        // When
        listener.onClosed(webSocket, 1000, "Normal closure");
        
        // Then
        verify(connectionHandler).handleConnectionClosed(eq("BINANCE"), eq(1000), eq("Normal closure"), eq(scheduleReconnectionCallback));
    }
    
    @Test
    void shouldHandleOnClosedWithAbnormalClosure() {
        // When
        listener.onClosed(webSocket, 1006, "Abnormal closure");
        
        // Then
        verify(connectionHandler).handleConnectionClosed(eq("BINANCE"), eq(1006), eq("Abnormal closure"), eq(scheduleReconnectionCallback));
    }
    
    @Test
    void shouldHandleOnFailureCorrectly() {
        // Given
        Throwable throwable = new RuntimeException("Connection failed");
        
        // When
        listener.onFailure(webSocket, throwable, response);
        
        // Then
        verify(connectionHandler).handleConnectionFailure(eq("BINANCE"), eq(throwable), eq(scheduleReconnectionCallback));
    }
    
    @Test
    void shouldCreateListenerWithRealObjectMapper() {
        // Given - Construtor de produção com ObjectMapper real
        BinanceWebSocketListener realListener = new BinanceWebSocketListener(
                connectionHandler,
                new ObjectMapper(),
                scheduleReconnectionCallback,
                onPriceUpdate,
                onOrderUpdate
        );
        
        // When
        String exchangeName = realListener.getExchangeName();
        
        // Then
        assertThat(exchangeName).isEqualTo("BINANCE");
        // Verifica que foi criado com strategy real internamente
    }
}