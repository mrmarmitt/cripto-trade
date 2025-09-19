package com.marmitt.core.ports.inbound.websocket;

import com.marmitt.core.dto.events.WebSocketClosedEvent;

/**
 * Port para lidar com eventos de conexão WebSocket fechada.
 * 
 * Define o contrato para processar eventos quando uma conexão WebSocket
 * é fechada (normal ou inesperadamente).
 */
public interface HandleConnectionClosedPort {
    
    /**
     * Processa evento de conexão fechada.
     * 
     * @param event Evento contendo detalhes da conexão fechada
     */
    void execute(WebSocketClosedEvent event);
}