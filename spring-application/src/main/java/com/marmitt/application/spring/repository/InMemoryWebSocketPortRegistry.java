package com.marmitt.application.spring.repository;

import com.marmitt.core.ports.outbound.websocket.WebSocketPort;
import com.marmitt.core.ports.outbound.websocket.WebSocketPortRegistryPort;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryWebSocketPortRegistry implements WebSocketPortRegistryPort {

    private final Map<String, WebSocketPort> marketPorts = new ConcurrentHashMap<>();
    private final Map<String, WebSocketPort> userStreamPorts = new ConcurrentHashMap<>();

    @Override
    public void register(String exchangeName, WebSocketPort port) {
        marketPorts.put(exchangeName.toUpperCase(), port);
    }

    @Override
    public Optional<WebSocketPort> findByExchangeName(String exchangeName) {
        return Optional.ofNullable(marketPorts.get(exchangeName.toUpperCase()));
    }

    @Override
    public void registerUserStream(String exchangeName, WebSocketPort port) {
        userStreamPorts.put(exchangeName.toUpperCase(), port);
    }

    @Override
    public Optional<WebSocketPort> findUserStreamByExchangeName(String exchangeName) {
        return Optional.ofNullable(userStreamPorts.get(exchangeName.toUpperCase()));
    }
}
