package com.marmitt.core.application.usecase;

import com.marmitt.core.domain.ConnectionResult;
import com.marmitt.core.dto.websocket.ConnectionResultMapper;
import com.marmitt.core.dto.websocket.WebSocketConnectionManager;
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
            WebSocketConnectionManager manager,
            ExchangeUrlBuilderPort exchangeUrlBuilderPort,
            WebSocketPort webSocketPort,
            WebSocketListenerPort listener) {

        ConnectionResult currentConnectionResult = manager.getConnectionResult();
        
        // Validation logic moved from service to use case
        if (currentConnectionResult.isConnected()) {
            return CompletableFuture.completedFuture(
                ConnectionResultMapper.toResponse(
                    currentConnectionResult.withMetadata("message", "Already connected")
                )
            );
        }

        if (currentConnectionResult.isInProgress()) {
            return CompletableFuture.completedFuture(
                ConnectionResultMapper.toResponse(currentConnectionResult)
            );
        }

        manager.startConnection();

        // Proceed with new connection
        String connectionUrl = exchangeUrlBuilderPort.buildConnectionUrl(parameters);
        return webSocketPort.connect(connectionUrl, listener, manager)
                .thenApply(ConnectionResultMapper::toResponse);
    }
}