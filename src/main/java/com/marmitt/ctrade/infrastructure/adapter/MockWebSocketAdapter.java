package com.marmitt.ctrade.infrastructure.adapter;

import com.marmitt.ctrade.domain.port.WebSocketPort;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

@Component
@Slf4j
public class MockWebSocketAdapter implements WebSocketPort {
    
    private boolean connected = false;
    private final Set<String> subscribedPairs = new HashSet<>();
    @Getter
    private boolean orderUpdatesSubscribed = false;
    
    @Override
    public void connect() {
        log.info("Mock WebSocket connecting...");
        connected = true;
        log.info("Mock WebSocket connected successfully");
    }
    
    @Override
    public void disconnect() {
        log.info("Mock WebSocket disconnecting...");
        connected = false;
        subscribedPairs.clear();
        orderUpdatesSubscribed = false;
        log.info("Mock WebSocket disconnected");
    }
    
    @Override
    public boolean isConnected() {
        return connected;
    }
    
    @Override
    public void subscribeToPrice(String tradingPair) {
        if (!connected) {
            log.warn("Cannot subscribe to {}: WebSocket not connected", tradingPair);
            return;
        }
        
        subscribedPairs.add(tradingPair);
        log.info("Mock WebSocket subscribed to price updates for: {}", tradingPair);
    }
    
    @Override
    public void subscribeToOrderUpdates() {
        if (!connected) {
            log.warn("Cannot subscribe to order updates: WebSocket not connected");
            return;
        }
        
        orderUpdatesSubscribed = true;
        log.info("Mock WebSocket subscribed to order updates");
    }
    
    public Set<String> getSubscribedPairs() {
        return new HashSet<>(subscribedPairs);
    }

}