package com.marmitt.ctrade.infrastructure.exchange.mock;

import com.marmitt.ctrade.infrastructure.config.WebSocketProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.TaskScheduler;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class MockWebSocketAdapterTest {
    
    private MockWebSocketAdapter adapter;
    private WebSocketProperties properties;
    private TaskScheduler taskScheduler;
    
    @BeforeEach
    void setUp() {
        properties = new WebSocketProperties();
        properties.setUrl("ws://localhost:8080/test");
        properties.setConnectionTimeout(Duration.ofSeconds(5));
        properties.setMaxRetries(1);
        taskScheduler = mock(TaskScheduler.class);
        adapter = new MockWebSocketAdapter(properties, taskScheduler);
    }
    
    @Test
    void shouldConnectAndDisconnect() {
        assertThat(adapter.isConnected()).isFalse();
        
        adapter.connect();
        assertThat(adapter.isConnected()).isTrue();
        
        adapter.disconnect();
        assertThat(adapter.isConnected()).isFalse();
    }
    
    @Test
    void shouldSubscribeToPriceUpdates() {
        adapter.connect();
        
        adapter.subscribeToPrice("BTC/USD");
        assertThat(adapter.getSubscribedPairs()).contains("BTC/USD");
        
        adapter.subscribeToPrice("ETH/USD");
        assertThat(adapter.getSubscribedPairs()).hasSize(2);
    }
    
    @Test
    void shouldSubscribeToOrderUpdates() {
        adapter.connect();
        
        adapter.subscribeToOrderUpdates();
        assertThat(adapter.isOrderUpdatesSubscribed()).isTrue();
    }
    
    @Test
    void shouldNotSubscribeWhenDisconnected() {
        adapter.subscribeToPrice("BTC/USD");
        adapter.subscribeToOrderUpdates();
        
        assertThat(adapter.getSubscribedPairs()).isEmpty();
        assertThat(adapter.isOrderUpdatesSubscribed()).isFalse();
    }
    
    @Test
    void shouldClearSubscriptionsOnDisconnect() {
        adapter.connect();
        adapter.subscribeToPrice("BTC/USD");
        adapter.subscribeToOrderUpdates();
        
        adapter.disconnect();
        
        assertThat(adapter.getSubscribedPairs()).isEmpty();
        assertThat(adapter.isOrderUpdatesSubscribed()).isFalse();
    }
}