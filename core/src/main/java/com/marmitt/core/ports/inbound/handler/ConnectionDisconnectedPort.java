package com.marmitt.core.ports.inbound.handler;

import com.marmitt.core.dto.events.WebSocketDisconnectedEvent;

/**
 * Port para lidar com eventos de desconexão WebSocket.
 * 
 * Define o contrato para processar eventos quando uma conexão WebSocket
 * é desconectada manualmente ou automaticamente.
 */
public interface ConnectionDisconnectedPort {
    
    /**
     * Processa evento de desconexão.
     * 
     * @param event Evento contendo detalhes da desconexão
     */
    void execute(WebSocketDisconnectedEvent event);
}