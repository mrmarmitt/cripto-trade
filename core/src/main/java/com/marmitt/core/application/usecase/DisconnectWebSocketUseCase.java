package com.marmitt.core.application.usecase;

import com.marmitt.core.dto.websocket.ConnectionResultMapper;
import com.marmitt.core.dto.websocket.WebSocketConnectionResponse;
import com.marmitt.core.ports.inbound.websocket.DisconnectWebSocketPort;
import com.marmitt.core.ports.outbound.websocket.WebSocketPort;

import java.util.concurrent.CompletableFuture;

public class DisconnectWebSocketUseCase implements DisconnectWebSocketPort {

    @Override
    public CompletableFuture<WebSocketConnectionResponse> execute(WebSocketPort webSocketPort) {
        return webSocketPort.disconnect()
                .thenApply(connectionResult -> ConnectionResultMapper.toResponse(connectionResult, ""));
    }
}