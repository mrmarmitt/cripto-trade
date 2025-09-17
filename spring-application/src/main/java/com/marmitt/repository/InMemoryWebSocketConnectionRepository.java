package com.marmitt.repository;

import com.marmitt.core.dto.websocket.WebSocketConnectionManager;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryWebSocketConnectionRepository {

    private final Map<String, WebSocketConnectionManager> connections = new ConcurrentHashMap<>();

    public void registerConnection(String exchangeName) {
        if (connections.get(exchangeName) == null) {
            connections.put(
                    exchangeName,
                    WebSocketConnectionManager.forExchange(exchangeName)
            );
        }
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