package com.marmitt.core.ports.outbound.exchange.adapter;

import com.marmitt.core.ports.outbound.websocket.WebSocketPort;

public interface ExchangeAdapterPort {
    
    String getExchangeName();

    boolean requiresPostConnection();

    WebSocketPort getWebSocketPort();

    ReceivedMessageProcessorPort getReceivedMessageProcessor();

    SenderMessageProcessorPort getSenderMessageProcessor();

    ExchangeUrlBuilderPort getUrlBuilder();

}