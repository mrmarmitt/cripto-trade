package com.marmitt.core.ports.outbound.repository;

import com.marmitt.core.dto.connection.ConnectionKey;
import com.marmitt.core.dto.wrapper.WebSocketConnectionManager;

import java.util.Map;
import java.util.Set;

public interface WebSocketConnectionRepositoryPort {

    void registerConnection(ConnectionKey key);

    WebSocketConnectionManager getConnection(ConnectionKey key);

    boolean hasConnection(ConnectionKey key);

    Set<String> getAllExchangeNames();

    Map<ConnectionKey, WebSocketConnectionManager> getAllConnections();
}
