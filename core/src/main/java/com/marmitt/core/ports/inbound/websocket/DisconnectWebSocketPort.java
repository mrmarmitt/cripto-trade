package com.marmitt.core.ports.inbound.websocket;

import com.marmitt.core.dto.websocket.WebSocketConnectionResponse;
import com.marmitt.core.ports.outbound.WebSocketPort;

import java.util.concurrent.CompletableFuture;

public interface DisconnectWebSocketPort {

    CompletableFuture<WebSocketConnectionResponse> execute(WebSocketPort webSocketPort);

}