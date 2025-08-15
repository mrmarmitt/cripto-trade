package com.marmitt.ctrade.infrastructure.adapter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MockWebSocketAdapterTest {
    
    private MockWebSocketAdapter adapter;
    
    @BeforeEach
    void setUp() {
        adapter = new MockWebSocketAdapter();
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