package com.marmitt.mock.adapter;

import com.marmitt.core.ports.outbound.websocket.WebSocketPort;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

/**
 * WebSocket adapter que não faz nada (No-Op).
 * Mock não precisa de conexão WebSocket real.
 */
@Slf4j
public class NoOpWebSocketAdapter implements WebSocketPort {

    @Override
    public void connect(String url, String exchangeName, UUID connectionId) {
        log.debug("Mock WebSocket adapter - connect called but ignored (exchangeName: {}, connectionId: {})",
                exchangeName, connectionId);
        // Mock não conecta - não faz nada
    }

    @Override
    public void disconnect(String exchangeName, UUID currentConnectionId) {
        log.debug("Mock WebSocket adapter - disconnect called but ignored (exchangeName: {}, connectionId: {})",
                exchangeName, currentConnectionId);
        // Mock não desconecta - não faz nada
    }

    @Override
    public void sendMessage(String message) {
        log.debug("Mock WebSocket adapter - sendMessage called but ignored (message length: {})",
                message != null ? message.length() : 0);
        // Mock não envia - simulador já processou a mensagem
    }

    @Override
    public boolean isConnected() {
        // Mock nunca está "conectado" via WebSocket
        return false;
    }
}
