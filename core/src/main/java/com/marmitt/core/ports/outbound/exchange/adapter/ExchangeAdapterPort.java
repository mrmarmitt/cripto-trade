package com.marmitt.core.ports.outbound.exchange.adapter;

import com.marmitt.core.ports.outbound.websocket.WebSocketPort;

/**
 * Interface que encapsula todos os componentes necessários para uma exchange específica.
 * 
 * Cada exchange deve implementar esta interface fornecendo:
 * - WebSocketPort: Responsável pela conexão WebSocket
 * - AdapterMessageProcessorPort: Processamento de mensagens específicas da exchange
 * - ExchangeUrlBuilderPort: Construção de URLs específicas da exchange
 * 
 * Esta interface permite o padrão Strategy para diferentes exchanges,
 * eliminando a necessidade de código específico por exchange no service principal.
 */
public interface ExchangeAdapterPort {
    
    String getExchangeName();

    boolean requiresPostConnection();

    WebSocketPort getWebSocketPort();

    ReceivedMessageProcessorPort getReceivedMessageProcessor();

    SenderMessageProcessorPort getSenderMessageProcessor();

    ExchangeUrlBuilderPort getUrlBuilder();

}