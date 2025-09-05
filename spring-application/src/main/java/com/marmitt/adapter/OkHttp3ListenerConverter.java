package com.marmitt.adapter;

import com.marmitt.core.dto.websocket.WebSocketConnectionManager;
import com.marmitt.core.ports.outbound.WebSocketListenerPort;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

@Component
public class OkHttp3ListenerConverter {

    public WebSocketListener convert(WebSocketListenerPort listener, WebSocketConnectionManager webSocketConnectionManager){

        return new WebSocketListener() {
            @Override
            public void onOpen(@NotNull WebSocket webSocket, @NotNull Response response) {
                webSocketConnectionManager.onConnected();
            }

            @Override
            public void onMessage(@NotNull WebSocket webSocket, @NotNull String text) {
                listener.onMessage(text);
            }

            @Override
            public void onClosing(@NotNull WebSocket webSocket, int code, @NotNull String reason) {
                webSocketConnectionManager.onClosing(code, reason);
            }

            @Override
            public void onClosed(@NotNull WebSocket webSocket, int code, @NotNull String reason){
                webSocketConnectionManager.onClosed(code, reason);
            }

            @Override
            public void onFailure(@NotNull WebSocket webSocket, @NotNull Throwable throwable, Response response) {
                webSocketConnectionManager.onFailure(throwable.getMessage(), throwable);
            }
        };
    }
}
