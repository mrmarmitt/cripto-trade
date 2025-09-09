package com.marmitt.core.ports.outbound.websocket;

import com.marmitt.core.domain.ConnectionResult;
import com.marmitt.core.dto.websocket.WebSocketConnectionManager;
import java.util.concurrent.CompletableFuture;

public interface WebSocketPort {

    CompletableFuture<ConnectionResult> connect(String url, WebSocketConnectionManager manager);

    CompletableFuture<ConnectionResult> disconnect();

    CompletableFuture<ConnectionResult> sendMessage(String message);

    boolean isConnected();

    ConnectionResult getConnectionResult();
}