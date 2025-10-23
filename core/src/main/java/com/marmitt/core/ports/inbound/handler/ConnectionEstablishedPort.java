package com.marmitt.core.ports.inbound.handler;

import com.marmitt.core.dto.events.WebSocketConnectedEvent;

/**
 * Port para lidar com eventos de conexão WebSocket estabelecida.
 * 
 * Define o contrato para processar eventos quando uma conexão WebSocket
 * é estabelecida com sucesso.
 */
public interface ConnectionEstablishedPort {
    
    /**
     * Processa evento de conexão estabelecida.
     * 
     * @param event Evento contendo detalhes da conexão estabelecida
     */
    void execute(WebSocketConnectedEvent event);
}