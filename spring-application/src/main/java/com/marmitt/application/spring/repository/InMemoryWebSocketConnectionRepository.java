package com.marmitt.application.spring.repository;

import com.marmitt.core.dto.connection.ConnectionKey;
import com.marmitt.core.dto.wrapper.WebSocketConnectionManager;
import com.marmitt.core.ports.outbound.repository.WebSocketConnectionRepositoryPort;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Repository
public class InMemoryWebSocketConnectionRepository implements WebSocketConnectionRepositoryPort {

    private final Map<ConnectionKey, WebSocketConnectionManager> connections = new ConcurrentHashMap<>();

    @PostConstruct
    public void initExchangeConnectionManager() {
        registerConnection(ConnectionKey.market("MOCK"));
    }

    @Override
    public void registerConnection(ConnectionKey key) {
        connections.computeIfAbsent(key, k -> WebSocketConnectionManager.forExchange(k.exchangeName()));
    }

    @Override
    public WebSocketConnectionManager getConnection(ConnectionKey key) {
        return connections.get(key);
    }

    @Override
    public boolean hasConnection(ConnectionKey key) {
        return connections.containsKey(key);
    }

    @Override
    public Set<String> getAllExchangeNames() {
        return connections.keySet().stream()
                .map(ConnectionKey::exchangeName)
                .collect(Collectors.toSet());
    }

    @Override
    public Map<ConnectionKey, WebSocketConnectionManager> getAllConnections() {
        return Map.copyOf(connections);
    }
}
