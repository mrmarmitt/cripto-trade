package com.marmitt.core.ports.inbound.websocket;

import com.marmitt.core.dto.events.WebSocketFailedEvent;

/**
 * Port para lidar com eventos de falha na conexão WebSocket.
 * 
 * Define o contrato para processar eventos quando uma conexão WebSocket
 * falha ao ser estabelecida ou encontra um erro.
 */
public interface HandleConnectionFailedPort {
    
    /**
     * Processa evento de falha na conexão.
     * 
     * @param event Evento contendo detalhes da falha na conexão
     */
    void execute(WebSocketFailedEvent event);
}