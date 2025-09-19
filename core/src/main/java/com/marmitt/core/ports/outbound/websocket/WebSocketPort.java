package com.marmitt.core.ports.outbound.websocket;

import java.util.UUID;

public interface WebSocketPort {

    void connect(String url, String exchangeName, UUID connectionId);

    void disconnect(String exchangeName, UUID currentConnectionId);

    void sendMessage(String message);

    boolean isConnected();
}