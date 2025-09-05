package com.marmitt.service;

import com.marmitt.core.dto.websocket.WebSocketConnectionManager;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.Set;

@Component
public class WebSocketConnectionRegistry {
    
    private final Map<String, WebSocketConnectionManager> connections = new ConcurrentHashMap<>();
    
    public WebSocketConnectionManager getOrCreateConnection(String exchangeName) {
        return connections.computeIfAbsent(
            exchangeName, 
            WebSocketConnectionManager::forExchange
        );
    }
    
    public WebSocketConnectionManager getConnection(String exchangeName) {
        return connections.get(exchangeName);
    }
    
    public boolean hasConnection(String exchangeName) {
        return connections.containsKey(exchangeName);
    }
    
    public Set<String> getAllExchangeNames() {
        return connections.keySet();
    }
    
    public Map<String, WebSocketConnectionManager> getAllConnections() {
        return Map.copyOf(connections);
    }
}