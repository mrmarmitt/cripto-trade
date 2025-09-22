package com.marmitt.core.ports.outbound.repository;

import com.marmitt.core.dto.wrapper.WebSocketConnectionManager;

import java.util.Map;
import java.util.Set;

public interface WebSocketConnectionRepositoryPort {

    void registerConnection(String exchangeName);

    WebSocketConnectionManager getConnection(String exchangeName);

    boolean hasConnection(String exchangeName);

    Set<String> getAllExchangeNames();

    Map<String, WebSocketConnectionManager> getAllConnections();
}
