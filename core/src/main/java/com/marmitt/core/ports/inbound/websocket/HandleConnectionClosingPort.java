package com.marmitt.core.ports.inbound.websocket;

import com.marmitt.core.dto.events.WebSocketClosingEvent;

/**
 * Port para lidar com eventos de início do fechamento da conexão WebSocket.
 * 
 * Define o contrato para processar eventos quando uma conexão WebSocket
 * está iniciando o processo de fechamento.
 */
public interface HandleConnectionClosingPort {
    
    /**
     * Processa evento de início do fechamento da conexão.
     * 
     * @param event Evento contendo detalhes do início do fechamento
     */
    void execute(WebSocketClosingEvent event);
}