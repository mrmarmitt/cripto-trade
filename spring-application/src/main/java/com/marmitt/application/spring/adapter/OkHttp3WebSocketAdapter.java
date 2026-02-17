package com.marmitt.application.spring.adapter;

import com.marmitt.core.ports.outbound.websocket.WebSocketPort;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

import java.time.Duration;
import java.util.UUID;


@Slf4j
public class OkHttp3WebSocketAdapter implements WebSocketPort {

    private final OkHttpClient client;
    private final OkHttp3ListenerConverter okHttp3ListenerConverter;

    private WebSocket webSocket;

    public OkHttp3WebSocketAdapter(OkHttp3ListenerConverter okHttp3ListenerConverter) {
        this.okHttp3ListenerConverter = okHttp3ListenerConverter;
        this.client = new OkHttpClient.Builder()
                .readTimeout(Duration.ZERO)
                .pingInterval(Duration.ofSeconds(20))  // Envia ping a cada 20s para manter conexão ativa
                .build();
    }

    @Override
    public void connect(String url, String exchangeName, UUID connectionId) {
        Request request = new Request.Builder()
                .url(url)
                .build();

        WebSocketListener enhancedListener = okHttp3ListenerConverter.convert(exchangeName, connectionId);
        this.webSocket = client.newWebSocket(request, enhancedListener);
        
        log.info("WebSocket connection initiated for exchange: {}, connectionId: {}", exchangeName, connectionId);
    }

    @Override
    public void disconnect(String exchangeName, UUID currentConnectionId) {
        if (webSocket != null) {
            webSocket.close(1000, "Normal closure");
        }
    }

    @Override
    public void sendMessage(String message) {
        webSocket.send(message);
    }

    @Override
    public boolean isConnected() {
        return webSocket != null;
    }
}