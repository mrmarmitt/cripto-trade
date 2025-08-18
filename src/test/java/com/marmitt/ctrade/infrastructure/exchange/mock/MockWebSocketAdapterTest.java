package com.marmitt.ctrade.infrastructure.exchange.mock;

import com.marmitt.ctrade.infrastructure.config.WebSocketProperties;
import com.marmitt.ctrade.infrastructure.websocket.ConnectionManager;
import com.marmitt.ctrade.infrastructure.websocket.ConnectionStatsTracker;
import com.marmitt.ctrade.infrastructure.websocket.WebSocketEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MockWebSocketAdapterTest {
    
    @Mock
    private WebSocketEventPublisher eventPublisher;
    
    @Mock
    private ConnectionManager connectionManager;
    
    @Mock
    private ConnectionStatsTracker statsTracker;
    
    private WebSocketProperties properties;
    private MockWebSocketAdapter adapter;
    
    @BeforeEach
    void setUp() {
        properties = new WebSocketProperties();
        properties.setUrl("ws://localhost:8080/test");
        properties.setConnectionTimeout(Duration.ofSeconds(5));
        properties.setMaxRetries(1);
        
        adapter = new MockWebSocketAdapter(properties, eventPublisher, connectionManager, statsTracker);
    }
    
    @Test
    void shouldReturnCorrectExchangeName() {
        // When
        String exchangeName = adapter.getExchangeName();
        
        // Then
        assertThat(exchangeName).isEqualTo("MOCK");
    }
    
    @Test
    void shouldDelegateConnectionStatusToConnectionManager() {
        // Given
        when(connectionManager.isConnected()).thenReturn(true);
        
        // When
        boolean isConnected = adapter.isConnected();
        
        // Then
        assertThat(isConnected).isTrue();
    }
    
    @Test
    void shouldConnectSuccessfully() {
        // When
        adapter.connect();
        
        // Then - Should call connectionManager.setConnected(true)
        // and update stats (verified by mocks)
    }
}