package com.marmitt.application.spring.repository;

import com.marmitt.application.spring.config.exchange.BinanceExchangeAdapter;
import com.marmitt.application.spring.config.exchange.CoinbaseExchangeAdapter;
import com.marmitt.application.spring.config.exchange.MockExchangeAdapter;
import com.marmitt.core.dto.wrapper.WebSocketConnectionManager;
import com.marmitt.core.ports.outbound.repository.WebSocketConnectionRepositoryPort;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryWebSocketConnectionRepository implements WebSocketConnectionRepositoryPort {

    private final Map<String, WebSocketConnectionManager> connections = new ConcurrentHashMap<>();

    @PostConstruct
    public void initExchangeConnectionManager() {
        registerConnection("MOCK");
    }

    @Override
    public void registerConnection(String exchangeName) {
        if (connections.get(exchangeName) == null) {
            connections.put(
                    exchangeName,
                    WebSocketConnectionManager.forExchange(exchangeName)
            );
        }
    }

    @Override
    public WebSocketConnectionManager getConnection(String exchangeName) {
        return connections.get(exchangeName);
    }

    @Override
    public boolean hasConnection(String exchangeName) {
        return connections.containsKey(exchangeName);
    }

    @Override
    public Set<String> getAllExchangeNames() {
        return connections.keySet();
    }

    @Override
    public Map<String, WebSocketConnectionManager> getAllConnections() {
        return Map.copyOf(connections);
    }
}