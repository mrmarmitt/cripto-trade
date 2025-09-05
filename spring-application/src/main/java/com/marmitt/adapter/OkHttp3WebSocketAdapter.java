package com.marmitt.adapter;

import com.marmitt.core.domain.ConnectionResult;
import com.marmitt.core.dto.websocket.WebSocketConnectionManager;
import com.marmitt.core.enums.ConnectionStatus;
import com.marmitt.core.ports.outbound.WebSocketListenerPort;
import com.marmitt.core.ports.outbound.WebSocketPort;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;


@Component
public class OkHttp3WebSocketAdapter implements WebSocketPort {

    private final OkHttpClient client;
    private final OkHttp3ListenerConverter okHttp3ListenerConverter;

    private WebSocket webSocket;

    public OkHttp3WebSocketAdapter(OkHttp3ListenerConverter okHttp3ListenerConverter) {
        this.okHttp3ListenerConverter = okHttp3ListenerConverter;
        this.client = new OkHttpClient.Builder().readTimeout(Duration.ZERO).build();
    }

    @Override
    public CompletableFuture<ConnectionResult> connect(String url, WebSocketListenerPort listener) {
        Request request = new Request.Builder()
                .url(url)
                .build();

        // Cria um manager temporário apenas para esta conexão
        WebSocketConnectionManager manager = WebSocketConnectionManager.forExchange("GENERIC");
        CompletableFuture<ConnectionResult> connectionFuture = manager.startConnection();

        WebSocketListener enhancedListener = okHttp3ListenerConverter.convert(listener, manager);
        this.webSocket = client.newWebSocket(request, enhancedListener);

        return connectionFuture;
    }

    @Override
    public CompletableFuture<ConnectionResult> disconnect() {
        if (webSocket != null) {
            webSocket.close(1000, "Normal closure");
            // Retorna um resultado simples de desconexão
            return CompletableFuture.completedFuture(
                ConnectionResult.idle("GENERIC").disconnected("Manual disconnect")
            );
        }
        return CompletableFuture.completedFuture(
            ConnectionResult.idle("GENERIC").disconnected("No active connection")
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
        if (webSocket != null) {
            return ConnectionResult.idle("GENERIC").connected();
        }
        return ConnectionResult.idle("GENERIC");
    }
}