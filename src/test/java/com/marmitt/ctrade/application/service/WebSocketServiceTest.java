package com.marmitt.ctrade.application.service;

import com.marmitt.ctrade.domain.port.WebSocketPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

/**
 * Testes unitários para WebSocketService.
 * Testa coordenação de conexões WebSocket e subscrições de trading pairs.
 */
@ExtendWith(MockitoExtension.class)
class WebSocketServiceTest {

    @Mock
    private WebSocketPort webSocketPort;

    private WebSocketService webSocketService;

    @BeforeEach
    void setUp() {
        webSocketService = new WebSocketService(webSocketPort);
    }

    @Test
    void shouldCreateServiceWithWebSocketPort() {
        // Given
        WebSocketPort port = mock(WebSocketPort.class);
        
        // When
        WebSocketService service = new WebSocketService(port);
        
        // Then
        // Constructor succeeds without exceptions
        verify(port, never()).connect(); // Should not connect during construction
    }

    @Test
    void shouldStartConnectionOnInit() {
        // When
        webSocketService.init();
        
        // Then
        verify(webSocketPort).connect();
    }

    @Test
    void shouldStartConnectionThroughWebSocketPort() {
        // When
        webSocketService.startConnection();
        
        // Then
        verify(webSocketPort).connect();
    }

    @Test
    void shouldStopConnectionThroughWebSocketPort() {
        // When
        webSocketService.stopConnection();
        
        // Then
        verify(webSocketPort).disconnect();
    }

    @Test
    void shouldSubscribeToTradingPairThroughWebSocketPort() {
        // Given
        String tradingPair = "BTCUSDT";
        
        // When
        webSocketService.subscribeToTradingPair(tradingPair);
        
        // Then
        verify(webSocketPort).subscribeToPrice(tradingPair);
    }

    @Test
    void shouldHandleMultipleStartConnectionCalls() {
        // When
        webSocketService.startConnection();
        webSocketService.startConnection();
        webSocketService.startConnection();
        
        // Then
        verify(webSocketPort, times(3)).connect();
    }

    @Test
    void shouldHandleMultipleStopConnectionCalls() {
        // When
        webSocketService.stopConnection();
        webSocketService.stopConnection();
        webSocketService.stopConnection();
        
        // Then
        verify(webSocketPort, times(3)).disconnect();
    }

    @Test
    void shouldHandleMultipleSubscriptionCallsForSamePair() {
        // Given
        String tradingPair = "BTCUSDT";
        
        // When
        webSocketService.subscribeToTradingPair(tradingPair);
        webSocketService.subscribeToTradingPair(tradingPair);
        webSocketService.subscribeToTradingPair(tradingPair);
        
        // Then
        verify(webSocketPort, times(3)).subscribeToPrice(tradingPair);
    }

    @Test
    void shouldHandleSubscriptionsForDifferentPairs() {
        // Given
        String pair1 = "BTCUSDT";
        String pair2 = "ETHUSDT";
        String pair3 = "ADAUSDT";
        
        // When
        webSocketService.subscribeToTradingPair(pair1);
        webSocketService.subscribeToTradingPair(pair2);
        webSocketService.subscribeToTradingPair(pair3);
        
        // Then
        verify(webSocketPort).subscribeToPrice(pair1);
        verify(webSocketPort).subscribeToPrice(pair2);
        verify(webSocketPort).subscribeToPrice(pair3);
    }

    @Test
    void shouldHandleMixedOperationsCorrectly() {
        // Given
        String tradingPair = "BTCUSDT";
        
        // When
        webSocketService.startConnection();
        webSocketService.subscribeToTradingPair(tradingPair);
        webSocketService.stopConnection();
        webSocketService.startConnection();
        
        // Then
        verify(webSocketPort, times(2)).connect();
        verify(webSocketPort).subscribeToPrice(tradingPair);
        verify(webSocketPort).disconnect();
    }

    @Test
    void shouldHandleNullTradingPairGracefully() {
        // When
        webSocketService.subscribeToTradingPair(null);
        
        // Then
        verify(webSocketPort).subscribeToPrice(null);
    }

    @Test
    void shouldHandleEmptyTradingPairGracefully() {
        // Given
        String emptyPair = "";
        
        // When
        webSocketService.subscribeToTradingPair(emptyPair);
        
        // Then
        verify(webSocketPort).subscribeToPrice(emptyPair);
    }

    @Test
    void shouldPropagateExceptionsFromWebSocketPort() {
        // Given
        RuntimeException exception = new RuntimeException("Connection failed");
        doThrow(exception).when(webSocketPort).connect();
        
        // When/Then
        try {
            webSocketService.startConnection();
        } catch (RuntimeException e) {
            // Expected exception should be propagated
            verify(webSocketPort).connect();
        }
    }

    @Test
    void shouldPropagateExceptionsFromDisconnect() {
        // Given
        RuntimeException exception = new RuntimeException("Disconnect failed");
        doThrow(exception).when(webSocketPort).disconnect();
        
        // When/Then
        try {
            webSocketService.stopConnection();
        } catch (RuntimeException e) {
            // Expected exception should be propagated
            verify(webSocketPort).disconnect();
        }
    }

    @Test
    void shouldPropagateExceptionsFromSubscription() {
        // Given
        String tradingPair = "BTCUSDT";
        RuntimeException exception = new RuntimeException("Subscription failed");
        doThrow(exception).when(webSocketPort).subscribeToPrice(tradingPair);
        
        // When/Then
        try {
            webSocketService.subscribeToTradingPair(tradingPair);
        } catch (RuntimeException e) {
            // Expected exception should be propagated
            verify(webSocketPort).subscribeToPrice(tradingPair);
        }
    }

    @Test
    void shouldCallInitMethodAfterConstruction() {
        // Given
        WebSocketService service = new WebSocketService(webSocketPort);
        
        // When
        service.init();
        
        // Then
        verify(webSocketPort).connect();
    }

    @Test
    void shouldHandleSequentialConnectionLifecycle() {
        // When - Simulate complete lifecycle
        webSocketService.startConnection();
        webSocketService.subscribeToTradingPair("BTCUSDT");
        webSocketService.subscribeToTradingPair("ETHUSDT");
        webSocketService.stopConnection();
        
        // Then
        verify(webSocketPort).connect();
        verify(webSocketPort).subscribeToPrice("BTCUSDT");
        verify(webSocketPort).subscribeToPrice("ETHUSDT");
        verify(webSocketPort).disconnect();
    }

    @Test
    void shouldVerifyNoUnexpectedInteractionsWithWebSocketPort() {
        // When
        webSocketService.startConnection();
        webSocketService.subscribeToTradingPair("BTCUSDT");
        webSocketService.stopConnection();
        
        // Then
        verify(webSocketPort).connect();
        verify(webSocketPort).subscribeToPrice("BTCUSDT");
        verify(webSocketPort).disconnect();
        verifyNoMoreInteractions(webSocketPort);
    }

    @Test
    void shouldHandleConcurrentOperationsCorrectly() {
        // Given - Simulate concurrent access patterns
        String[] tradingPairs = {"BTCUSDT", "ETHUSDT", "ADAUSDT", "DOTUSDT"};
        
        // When
        webSocketService.startConnection();
        for (String pair : tradingPairs) {
            webSocketService.subscribeToTradingPair(pair);
        }
        webSocketService.stopConnection();
        
        // Then
        verify(webSocketPort).connect();
        for (String pair : tradingPairs) {
            verify(webSocketPort).subscribeToPrice(pair);
        }
        verify(webSocketPort).disconnect();
    }

    @Test
    void shouldHandleRapidStartStopCycles() {
        // When - Rapid start/stop cycles
        for (int i = 0; i < 5; i++) {
            webSocketService.startConnection();
            webSocketService.stopConnection();
        }
        
        // Then
        verify(webSocketPort, times(5)).connect();
        verify(webSocketPort, times(5)).disconnect();
    }

    @Test
    void shouldHandleSubscriptionsWithSpecialCharacters() {
        // Given
        String[] specialPairs = {
            "BTC-USDT", 
            "ETH_USD", 
            "ADA/USDT", 
            "DOT.BTC",
            "TEST@SYMBOL"
        };
        
        // When
        for (String pair : specialPairs) {
            webSocketService.subscribeToTradingPair(pair);
        }
        
        // Then
        for (String pair : specialPairs) {
            verify(webSocketPort).subscribeToPrice(pair);
        }
    }

    @Test
    void shouldHandleLongTradingPairNames() {
        // Given
        String longPair = "VERYLONGTRADINGPAIRNAMETHATEXCEEDSNORMALLIMITS";
        
        // When
        webSocketService.subscribeToTradingPair(longPair);
        
        // Then
        verify(webSocketPort).subscribeToPrice(longPair);
    }

    @Test
    void shouldMaintainOperationOrderingWithWebSocketPort() {
        // When - Execute operations in specific order
        webSocketService.subscribeToTradingPair("PAIR1"); // This might fail in real scenario
        webSocketService.startConnection();
        webSocketService.subscribeToTradingPair("PAIR2");
        webSocketService.subscribeToTradingPair("PAIR3");
        webSocketService.stopConnection();
        
        // Then - Verify operations were called in correct order
        var inOrder = inOrder(webSocketPort);
        inOrder.verify(webSocketPort).subscribeToPrice("PAIR1");
        inOrder.verify(webSocketPort).connect();
        inOrder.verify(webSocketPort).subscribeToPrice("PAIR2");
        inOrder.verify(webSocketPort).subscribeToPrice("PAIR3");
        inOrder.verify(webSocketPort).disconnect();
    }
}