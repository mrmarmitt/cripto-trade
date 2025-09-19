package com.marmitt.adapter;

import com.marmitt.core.domain.ConnectionResult;
import com.marmitt.core.dto.websocket.MessageContext;
import com.marmitt.core.ports.outbound.events.EventPublisherPort;
import com.marmitt.event.RawMessageReceivedEvent;
import com.marmitt.core.dto.events.WebSocketConnectedEvent;
import com.marmitt.core.dto.events.WebSocketFailedEvent;
import com.marmitt.core.dto.events.WebSocketClosedEvent;
import com.marmitt.core.dto.events.WebSocketClosingEvent;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Converter que transforma eventos técnicos do OkHttp3 WebSocket em eventos de domínio.
 * <p>
 * Responsabilidades:
 * - Converter callbacks técnicos em eventos tipados
 * - Publicar eventos para processamento pelos listeners apropriados
 * - Manter separação entre infraestrutura (OkHttp3) e domínio (Events)
 * - Não gerenciar estado (delegado para ConnectionStateEventListener)
 */
@Component
@Slf4j
public class OkHttp3ListenerConverter {

    private final EventPublisherPort eventPublisher;

    public OkHttp3ListenerConverter(EventPublisherPort eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    /**
     * Converte eventos técnicos do OkHttp3 em eventos de domínio
     * e os publica para processamento pelos listeners.
     *
     * @param exchangeName     Nome da exchange
     * @param connectionId     ID único da conexão
     * @return WebSocketListener configurado para publicar eventos
     */
    public WebSocketListener convert(String exchangeName, UUID connectionId) {

        return new WebSocketListener() {

            @Override
            public void onOpen(@NotNull WebSocket webSocket, @NotNull Response response) {
                log.info("WebSocket opened for exchange: {}, connectionId: {}", exchangeName, connectionId);

                // Publica evento de conexão estabelecida
                WebSocketConnectedEvent event = WebSocketConnectedEvent.of(
                        exchangeName,
                        "Connection established successfully",
                        connectionId
                );
                eventPublisher.publishEvent(event);
            }

            @Override
            public void onMessage(@NotNull WebSocket webSocket, @NotNull String text) {
                log.debug("Message received from {}: length={}", exchangeName, text.length());

                // Cria contexto com correlationId único para rastreamento
                MessageContext context = MessageContext.create(exchangeName, connectionId);

                // Publica evento de mensagem (para MessageEventHandler)
                RawMessageReceivedEvent messageEvent = new RawMessageReceivedEvent(this, text, context);
                eventPublisher.publishEvent(messageEvent);
            }

            @Override
            public void onClosing(@NotNull WebSocket webSocket, int code, @NotNull String reason) {
                log.info("WebSocket closing for exchange: {} - Code: {}, Reason: {}",
                        exchangeName, code, reason);

                // Publica evento de fechamento iniciado
                WebSocketClosingEvent event = WebSocketClosingEvent.of(
                        exchangeName,
                        code,
                        reason,
                        connectionId
                );
                eventPublisher.publishEvent(event);
            }

            @Override
            public void onClosed(@NotNull WebSocket webSocket, int code, @NotNull String reason) {
                log.info("WebSocket closed for exchange: {} - Code: {}, Reason: {}",
                        exchangeName, code, reason);

                // Publica evento de conexão fechada
                WebSocketClosedEvent event = WebSocketClosedEvent.of(
                        exchangeName,
                        code,
                        reason,
                        connectionId
                );
                eventPublisher.publishEvent(event);
            }

            @Override
            public void onFailure(@NotNull WebSocket webSocket, @NotNull Throwable throwable, Response response) {
                log.error("WebSocket failure for exchange: {}, connectionId: {}", exchangeName, connectionId, throwable);

                // Publica evento de falha
                WebSocketFailedEvent event = WebSocketFailedEvent.of(
                        exchangeName,
                        "Connection failed: " + throwable.getMessage(),
                        connectionId,
                        throwable
                );
                eventPublisher.publishEvent(event);
            }
        };
    }
}
