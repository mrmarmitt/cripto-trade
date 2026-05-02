package com.marmitt.core.ports.outbound.exchange.streaming;

import com.marmitt.core.ports.outbound.exchange.adapter.ReceivedMessageProcessorPort;
import com.marmitt.core.ports.outbound.websocket.WebSocketPort;

public interface ExchangeUserStreamPort {

    String getExchangeName();

    WebSocketPort getWebSocketPort();

    ReceivedMessageProcessorPort getReceivedMessageProcessor();
}
