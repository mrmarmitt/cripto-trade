package com.marmitt.core.application.usecase;

import com.marmitt.core.domain.ConnectionResult;
import com.marmitt.core.dto.websocket.ConnectionResultMapper;
import com.marmitt.core.dto.websocket.WebSocketConnectionResponse;
import com.marmitt.core.dto.configuration.WebSocketConnectionParameters;
import com.marmitt.core.ports.inbound.websocket.ConnectWebSocketPort;
import com.marmitt.core.ports.outbound.ExchangeUrlBuilderPort;
import com.marmitt.core.ports.outbound.WebSocketListenerPort;
import com.marmitt.core.ports.outbound.WebSocketPort;

import java.util.concurrent.CompletableFuture;

public class ConnectWebSocketUseCase implements ConnectWebSocketPort {

    @Override
    public CompletableFuture<WebSocketConnectionResponse> execute(
            WebSocketConnectionParameters parameters,
            ExchangeUrlBuilderPort exchangeUrlBuilderPort,
            WebSocketPort webSocketPort,
            WebSocketListenerPort listener) {

        String connectionUrl = exchangeUrlBuilderPort.buildConnectionUrl(parameters);
        return webSocketPort.connect(connectionUrl, listener)
                .thenApply(ConnectionResultMapper::toResponse);
    }
}