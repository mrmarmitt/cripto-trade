package com.marmitt.core.ports.outbound;

import com.marmitt.core.ports.outbound.websocket.AdapterMessageProcessorPort;
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
    
    /**
     * Retorna o nome da exchange (ex: "BINANCE", "COINBASE")
     */
    String getExchangeName();

    /**
     * Retorna o WebSocketPort específico desta exchange
     */
    WebSocketPort getWebSocketPort();

    /**
     * Retorna o processador de mensagens específico desta exchange
     */
    AdapterMessageProcessorPort getMessageProcessor();
    
    /**
     * Retorna o construtor de URLs específico desta exchange
     */
    ExchangeUrlBuilderPort getUrlBuilder();
}