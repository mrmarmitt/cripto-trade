package com.marmitt.ctrade.application.service;

import com.marmitt.ctrade.controller.dto.HealthCheckResponse;
import com.marmitt.ctrade.infrastructure.exchange.mock.MockWebSocketAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HealthCheckServiceTest {
    
    @Mock
    private PriceCacheService priceCacheService;
    
    @Mock
    private MockWebSocketAdapter mockWebSocketAdapter;
    
    @InjectMocks
    private HealthCheckService healthCheckService;
    
    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(healthCheckService, "ttlMinutes", 5);
        ReflectionTestUtils.setField(healthCheckService, "maxHistorySize", 100);
        ReflectionTestUtils.setField(healthCheckService, "webSocketPort", mockWebSocketAdapter);
    }
    
    @Test
    void shouldReturnHealthyStatus() {
        // Mock cache
        when(priceCacheService.getCacheSize()).thenReturn(3);
        when(priceCacheService.getTotalHistoryEntries()).thenReturn(15);
        
        // Mock WebSocket
        when(mockWebSocketAdapter.isConnected()).thenReturn(true);
        when(mockWebSocketAdapter.getSubscribedPairs()).thenReturn(Set.of("BTC/USD", "ETH/USD"));
        when(mockWebSocketAdapter.isOrderUpdatesSubscribed()).thenReturn(true);
        
        HealthCheckResponse health = healthCheckService.getSystemHealth();
        
        assertThat(health.getStatus()).isEqualTo("UP");
        assertThat(health.getTimestamp()).isNotNull();
        
        // Cache info
        assertThat(health.getCache().getStatus()).isEqualTo("UP");
        assertThat(health.getCache().getTradingPairs()).isEqualTo(3);
        assertThat(health.getCache().getTotalEntries()).isEqualTo(15);
        assertThat(health.getCache().getTtlMinutes()).isEqualTo("5 minutes");
        assertThat(health.getCache().getMaxHistorySize()).isEqualTo(100);
        
        // WebSocket info
        assertThat(health.getWebSocket().getStatus()).isEqualTo("UP");
        assertThat(health.getWebSocket().isConnected()).isTrue();
        assertThat(health.getWebSocket().getSubscribedPairs()).isEqualTo(2);
        assertThat(health.getWebSocket().isOrderUpdatesSubscribed()).isTrue();
    }
    
    @Test
    void shouldReturnDegradedWhenWebSocketDown() {
        // Mock cache healthy
        when(priceCacheService.getCacheSize()).thenReturn(3);
        when(priceCacheService.getTotalHistoryEntries()).thenReturn(15);
        
        // Mock WebSocket down
        when(mockWebSocketAdapter.isConnected()).thenReturn(false);
        when(mockWebSocketAdapter.getSubscribedPairs()).thenReturn(Set.of());
        when(mockWebSocketAdapter.isOrderUpdatesSubscribed()).thenReturn(false);
        
        HealthCheckResponse health = healthCheckService.getSystemHealth();
        
        assertThat(health.getStatus()).isEqualTo("DEGRADED");
        assertThat(health.getCache().getStatus()).isEqualTo("UP");
        assertThat(health.getWebSocket().getStatus()).isEqualTo("DOWN");
    }
    
    @Test
    void shouldReturnDownWhenCacheFailsAndWebSocketDown() {
        // Mock cache failure
        when(priceCacheService.getCacheSize()).thenThrow(new RuntimeException("Cache error"));
        
        // Mock WebSocket down
        when(mockWebSocketAdapter.isConnected()).thenReturn(false);
        when(mockWebSocketAdapter.getSubscribedPairs()).thenReturn(Set.of());
        when(mockWebSocketAdapter.isOrderUpdatesSubscribed()).thenReturn(false);
        
        HealthCheckResponse health = healthCheckService.getSystemHealth();
        
        assertThat(health.getStatus()).isEqualTo("DOWN");
        assertThat(health.getCache().getStatus()).isEqualTo("DOWN");
        assertThat(health.getCache().getTradingPairs()).isZero();
        assertThat(health.getCache().getTotalEntries()).isZero();
        assertThat(health.getWebSocket().getStatus()).isEqualTo("DOWN");
    }
    
    @Test
    void shouldHandleWebSocketException() {
        // Mock cache healthy
        when(priceCacheService.getCacheSize()).thenReturn(1);
        when(priceCacheService.getTotalHistoryEntries()).thenReturn(5);
        
        // Mock WebSocket exception
        when(mockWebSocketAdapter.isConnected()).thenThrow(new RuntimeException("WebSocket error"));
        
        HealthCheckResponse health = healthCheckService.getSystemHealth();
        
        assertThat(health.getStatus()).isEqualTo("DEGRADED");
        assertThat(health.getCache().getStatus()).isEqualTo("UP");
        assertThat(health.getWebSocket().getStatus()).isEqualTo("DOWN");
        assertThat(health.getWebSocket().isConnected()).isFalse();
        assertThat(health.getWebSocket().getSubscribedPairs()).isZero();
    }
}