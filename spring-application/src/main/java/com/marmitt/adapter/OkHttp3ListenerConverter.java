package com.marmitt.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marmitt.core.dto.websocket.MessageContext;
import com.marmitt.core.dto.websocket.WebSocketConnectionManager;
import com.marmitt.core.ports.outbound.websocket.MessageProcessorPort;
import com.marmitt.event.RawMessageReceivedEvent;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import org.jetbrains.annotations.NotNull;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;

@Slf4j
public class OkHttp3ListenerConverter {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static WebSocketListener convert(MessageProcessorPort listener,
                                            WebSocketConnectionManager webSocketConnectionManager,
                                            ApplicationEventPublisher eventPublisher) {

        return new WebSocketListener() {
            @Override
            public void onOpen(@NotNull WebSocket webSocket, @NotNull Response response) {
                webSocketConnectionManager.onConnected();
            }

            @Override
            public void onMessage(@NotNull WebSocket webSocket, @NotNull String text) {

                // Cria contexto com correlationId único
                MessageContext context = MessageContext.create(webSocketConnectionManager.getExchangeName(), webSocketConnectionManager.getConnectionId());

                // Adiciona correlationId no MDC para logs correlacionados
                MDC.put("correlationId", context.correlationId().toString());

                webSocketConnectionManager.onMessageReceived();

                log.info("Message received: length={}, exchange={}", text.length(), webSocketConnectionManager.getExchangeName());

                // Publica evento com correlationId para processamento assíncrono
                RawMessageReceivedEvent event = new RawMessageReceivedEvent(this, text, context);
                eventPublisher.publishEvent(event);

                MDC.clear(); // Limpa MDC
            }

            @Override
            public void onClosing(@NotNull WebSocket webSocket, int code, @NotNull String reason) {
                webSocketConnectionManager.onClosing(code, reason);
            }

            @Override
            public void onClosed(@NotNull WebSocket webSocket, int code, @NotNull String reason) {
                webSocketConnectionManager.onClosed(code, reason);
            }

            @Override
            public void onFailure(@NotNull WebSocket webSocket, @NotNull Throwable throwable, Response response) {
                webSocketConnectionManager.onFailure(throwable.getMessage(), throwable);
            }
        };
    }
}
