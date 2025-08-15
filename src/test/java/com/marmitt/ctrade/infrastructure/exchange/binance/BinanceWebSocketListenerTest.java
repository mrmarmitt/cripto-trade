package com.marmitt.ctrade.infrastructure.exchange.binance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marmitt.ctrade.application.service.WebSocketService;
import com.marmitt.ctrade.domain.dto.PriceUpdateMessage;
import com.marmitt.ctrade.domain.port.ExchangeWebSocketAdapter;
import com.marmitt.ctrade.infrastructure.websocket.ReconnectionStrategy;
import com.marmitt.ctrade.infrastructure.websocket.WebSocketCircuitBreaker;
import okhttp3.Response;
import okhttp3.WebSocket;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
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
    
    private ObjectMapper objectMapper;
    private Set<String> subscribedPairs;
    private AtomicLong totalMessagesReceived;
    private AtomicLong totalErrors;
    private BinanceWebSocketListener listener;
    
    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        subscribedPairs = ConcurrentHashMap.newKeySet();
        totalMessagesReceived = new AtomicLong(0);
        totalErrors = new AtomicLong(0);
        
        listener = new BinanceWebSocketListener(
            objectMapper,
            webSocketService,
            subscribedPairs,
            reconnectionStrategy,
            circuitBreaker,
            totalMessagesReceived,
            totalErrors,
            statusUpdater,
            lastConnectedAtUpdater,
            lastMessageAtUpdater,
            scheduleReconnectionCallback
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
    void shouldProcessValidBinanceMessageArray() {
        // Given - Array format as returned by !ticker@arr endpoint
        String binanceMessageArray = """
            [
                {
                    "e": "24hrTicker",
                    "E": 1640995200000,
                    "s": "BTCUSD",
                    "c": "50000.00",
                    "P": "2.50",
                    "p": "1000.00",
                    "w": "49500.00",
                    "x": "49000.00",
                    "Q": "0.01",
                    "b": "49999.00",
                    "B": "1.0",
                    "a": "50001.00",
                    "A": "1.0",
                    "o": "49000.00",
                    "h": "50100.00",
                    "l": "48900.00",
                    "v": "1000.00",
                    "q": "49500000.00",
                    "O": 1640908800000,
                    "C": 1640995200000,
                    "F": 1,
                    "L": 1000,
                    "n": 1000
                },
                {
                    "e": "24hrTicker",
                    "E": 1640995200000,
                    "s": "ETHUSD",
                    "c": "3000.00",
                    "P": "1.50",
                    "p": "50.00",
                    "w": "2950.00",
                    "x": "2950.00",
                    "Q": "0.1",
                    "b": "2999.00",
                    "B": "1.0",
                    "a": "3001.00",
                    "A": "1.0",
                    "o": "2950.00",
                    "h": "3010.00",
                    "l": "2940.00",
                    "v": "100.00",
                    "q": "295000.00",
                    "O": 1640908800000,
                    "C": 1640995200000,
                    "F": 1,
                    "L": 100,
                    "n": 100
                }
            ]
            """;
        
        // When
        listener.onMessage(webSocket, binanceMessageArray);
        
        // Then
        verify(lastMessageAtUpdater).accept(any(LocalDateTime.class));
        assertThat(totalMessagesReceived.get()).isEqualTo(1);
        
        ArgumentCaptor<PriceUpdateMessage> messageCaptor = ArgumentCaptor.forClass(PriceUpdateMessage.class);
        verify(webSocketService, times(2)).handlePriceUpdate(messageCaptor.capture());
        
        var capturedMessages = messageCaptor.getAllValues();
        assertThat(capturedMessages).hasSize(2);
        
        // First message (BTCUSD)
        assertThat(capturedMessages.get(0).getTradingPair()).isEqualTo("BTCUSD");
        assertThat(capturedMessages.get(0).getPrice()).isEqualTo(new BigDecimal("50000.00"));
        
        // Second message (ETHUSD) 
        assertThat(capturedMessages.get(1).getTradingPair()).isEqualTo("ETHUSD");
        assertThat(capturedMessages.get(1).getPrice()).isEqualTo(new BigDecimal("3000.00"));
    }
    
    @Test
    void shouldIgnoreNonTickerMessages() {
        // Given - Array with non-ticker message
        String nonTickerMessage = """
            [
                {
                    "e": "trade",
                    "E": 1640995200000,
                    "s": "BTCUSD",
                    "p": "50000.00"
                }
            ]
            """;
        
        // When
        listener.onMessage(webSocket, nonTickerMessage);
        
        // Then
        verify(webSocketService, never()).handlePriceUpdate(any());
        assertThat(totalMessagesReceived.get()).isEqualTo(1);
    }
    
    @Test
    void shouldFilterBySubscribedPairs() {
        // Given
        subscribedPairs.add("ETHUSD");
        
        String mixedMessage = """
            [
                {
                    "e": "24hrTicker",
                    "E": 1640995200000,
                    "s": "BTCUSD",
                    "c": "50000.00",
                    "P": "2.50",
                    "p": "1000.00",
                    "w": "49500.00",
                    "x": "49000.00",
                    "Q": "0.01",
                    "b": "49999.00",
                    "B": "1.0",
                    "a": "50001.00",
                    "A": "1.0",
                    "o": "49000.00",
                    "h": "50100.00",
                    "l": "48900.00",
                    "v": "1000.00",
                    "q": "49500000.00",
                    "O": 1640908800000,
                    "C": 1640995200000,
                    "F": 1,
                    "L": 1000,
                    "n": 1000
                },
                {
                    "e": "24hrTicker",
                    "E": 1640995200000,
                    "s": "ETHUSD",
                    "c": "3000.00",
                    "P": "1.50",
                    "p": "50.00",
                    "w": "2950.00",
                    "x": "2950.00",
                    "Q": "0.1",
                    "b": "2999.00",
                    "B": "1.0",
                    "a": "3001.00",
                    "A": "1.0",
                    "o": "2950.00",
                    "h": "3010.00",
                    "l": "2940.00",
                    "v": "100.00",
                    "q": "295000.00",
                    "O": 1640908800000,
                    "C": 1640995200000,
                    "F": 1,
                    "L": 100,
                    "n": 100
                }
            ]
            """;
        
        // When
        listener.onMessage(webSocket, mixedMessage); // Should only process ETHUSD
        
        // Then - Only ETHUSD should be processed (1 call), BTCUSD ignored
        verify(webSocketService, times(1)).handlePriceUpdate(any());
        assertThat(totalMessagesReceived.get()).isEqualTo(1);
        
        ArgumentCaptor<PriceUpdateMessage> messageCaptor = ArgumentCaptor.forClass(PriceUpdateMessage.class);
        verify(webSocketService).handlePriceUpdate(messageCaptor.capture());
        
        PriceUpdateMessage capturedMessage = messageCaptor.getValue();
        assertThat(capturedMessage.getTradingPair()).isEqualTo("ETHUSD");
        assertThat(capturedMessage.getPrice()).isEqualTo(new BigDecimal("3000.00"));
    }
    
    @Test
    void shouldHandleParsingErrors() {
        // Given
        String invalidMessage = "invalid json";
        
        // When
        listener.onMessage(webSocket, invalidMessage);
        
        // Then
        verify(webSocketService, never()).handlePriceUpdate(any());
        assertThat(totalErrors.get()).isEqualTo(1);
        assertThat(totalMessagesReceived.get()).isEqualTo(1);
    }
    
    @Test
    void shouldHandleOnClosed() {
        // Given
        int code = 1006; // Abnormal closure
        String reason = "Connection lost";
        
        // When
        listener.onClosed(webSocket, code, reason);
        
        // Then
        verify(statusUpdater).accept(ExchangeWebSocketAdapter.ConnectionStatus.DISCONNECTED);
        verify(circuitBreaker).recordFailure();
        verify(scheduleReconnectionCallback).run();
    }
    
    @Test
    void shouldNotScheduleReconnectionOnNormalClosure() {
        // Given
        int code = 1000; // Normal closure
        String reason = "Normal close";
        
        // When
        listener.onClosed(webSocket, code, reason);
        
        // Then
        verify(statusUpdater).accept(ExchangeWebSocketAdapter.ConnectionStatus.DISCONNECTED);
        verify(circuitBreaker, never()).recordFailure();
        verify(scheduleReconnectionCallback, never()).run();
    }
    
    @Test
    void shouldHandleOnFailure() {
        // Given
        Throwable throwable = new RuntimeException("Connection failed");
        
        // When
        listener.onFailure(webSocket, throwable, response);
        
        // Then
        verify(statusUpdater).accept(ExchangeWebSocketAdapter.ConnectionStatus.FAILED);
        verify(circuitBreaker).recordFailure();
        verify(scheduleReconnectionCallback).run();
        assertThat(totalErrors.get()).isEqualTo(1);
    }
}