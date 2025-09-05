package com.marmitt.core.ports.outbound;

import com.marmitt.core.domain.ConnectionResult;
import java.util.concurrent.CompletableFuture;

public interface WebSocketPort {

    CompletableFuture<ConnectionResult> connect(String url, WebSocketListenerPort listener);

    CompletableFuture<ConnectionResult> disconnect();

    CompletableFuture<ConnectionResult> sendMessage(String message);

    boolean isConnected();

    ConnectionResult getConnectionResult();
}