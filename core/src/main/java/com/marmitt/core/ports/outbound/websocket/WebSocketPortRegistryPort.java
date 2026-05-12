package com.marmitt.core.ports.outbound.websocket;

import java.util.Optional;

public interface WebSocketPortRegistryPort {

    void register(String exchangeName, WebSocketPort port);

    Optional<WebSocketPort> findByExchangeName(String exchangeName);
}
