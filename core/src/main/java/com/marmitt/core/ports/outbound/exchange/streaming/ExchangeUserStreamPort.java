package com.marmitt.core.ports.outbound.exchange.streaming;

import com.marmitt.core.ports.outbound.exchange.adapter.ReceivedMessageProcessorPort;
import com.marmitt.core.ports.outbound.websocket.WebSocketPort;

import java.io.IOException;
import java.util.UUID;

public interface ExchangeUserStreamPort {

    String getExchangeName();

    WebSocketPort getWebSocketPort();

    ReceivedMessageProcessorPort getReceivedMessageProcessor();

    void connect(UUID connectionId) throws IOException;

    void disconnect(UUID connectionId);
}
