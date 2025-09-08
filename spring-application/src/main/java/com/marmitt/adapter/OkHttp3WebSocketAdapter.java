package com.marmitt.adapter;

import com.marmitt.core.domain.ConnectionResult;
import com.marmitt.core.dto.websocket.WebSocketConnectionManager;
import com.marmitt.core.ports.outbound.websocket.MessageProcessorPort;
import com.marmitt.core.ports.outbound.websocket.WebSocketPort;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;


public class OkHttp3WebSocketAdapter implements WebSocketPort {

    private final OkHttpClient client;
    private final ApplicationEventPublisher eventPublisher;

    private WebSocket webSocket;
    private WebSocketConnectionManager currentManager;

    public OkHttp3WebSocketAdapter(ApplicationEventPublisher eventPublisher) {
        this.client = new OkHttpClient.Builder().readTimeout(Duration.ZERO).build();
        this.eventPublisher = eventPublisher;
    }

    @Override
    public CompletableFuture<ConnectionResult> connect(String url, MessageProcessorPort listener, WebSocketConnectionManager manager) {
        Request request = new Request.Builder()
                .url(url)
                .build();

        // Usa o manager fornecido pela camada superior
        this.currentManager = manager;
        CompletableFuture<ConnectionResult> connectionFuture = manager.startConnection();

        WebSocketListener enhancedListener = OkHttp3ListenerConverter.convert(listener, manager, eventPublisher);
        this.webSocket = client.newWebSocket(request, enhancedListener);
        return connectionFuture;
    }

    @Override
    public CompletableFuture<ConnectionResult> disconnect() {
        if (webSocket != null) {
            webSocket.close(1000, "Normal closure");
            if (currentManager != null) {
                currentManager.startDisconnection();
                return CompletableFuture.completedFuture(
                    currentManager.getConnectionResult()
                );
            }
        }
        
        // Fallback se não há manager
        return CompletableFuture.completedFuture(
            ConnectionResult.idle().disconnected("No active connection")
        );
    }

    @Override
    public CompletableFuture<ConnectionResult> sendMessage(String message) {
        if (webSocket == null || !isConnected()) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("WebSocket not connected")
            );
        }

        boolean sent = webSocket.send(message);
        if (sent) {
            return CompletableFuture.completedFuture(null);
        } else {
            return CompletableFuture.failedFuture(
                    new RuntimeException("Failed to send message")
            );
        }
    }

    @Override
    public boolean isConnected() {
        return webSocket != null;
    }

    @Override
    public ConnectionResult getConnectionResult() {
        if (currentManager != null) {
            return currentManager.getConnectionResult();
        }
        
        // Fallback se não há manager
        if (webSocket != null) {
            return ConnectionResult.idle().connected();
        }
        return ConnectionResult.idle();
    }
}