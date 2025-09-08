package com.marmitt.core.ports.inbound.websocket;

import com.marmitt.core.dto.websocket.WebSocketConnectionManager;
import com.marmitt.core.dto.websocket.WebSocketConnectionResponse;
import com.marmitt.core.dto.configuration.WebSocketConnectionParameters;
import com.marmitt.core.ports.outbound.ExchangeUrlBuilderPort;
import com.marmitt.core.ports.outbound.websocket.MessageProcessorPort;
import com.marmitt.core.ports.outbound.websocket.WebSocketPort;

import java.util.concurrent.CompletableFuture;

public interface ConnectWebSocketPort {

    CompletableFuture<WebSocketConnectionResponse> execute(
            WebSocketConnectionParameters parameters,
            WebSocketConnectionManager manager,
            ExchangeUrlBuilderPort exchangeUrlBuilderPort,
            WebSocketPort webSocketPort,
            MessageProcessorPort listener);

}