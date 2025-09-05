package com.marmitt.core.ports.inbound.websocket;

import com.marmitt.core.domain.ConnectionResult;
import com.marmitt.core.dto.websocket.WebSocketConnectionResponse;
import com.marmitt.core.dto.configuration.WebSocketConnectionParameters;
import com.marmitt.core.ports.outbound.ExchangeUrlBuilderPort;
import com.marmitt.core.ports.outbound.WebSocketListenerPort;
import com.marmitt.core.ports.outbound.WebSocketPort;

import java.util.concurrent.CompletableFuture;

public interface ConnectWebSocketPort {

    CompletableFuture<WebSocketConnectionResponse> execute(
            WebSocketConnectionParameters parameters,
            ConnectionResult currentConnectionResult,
            ExchangeUrlBuilderPort exchangeUrlBuilderPort,
            WebSocketPort webSocketPort,
            WebSocketListenerPort listener);

}