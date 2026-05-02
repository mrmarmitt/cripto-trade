package com.marmitt.application.spring.adapter;

import com.marmitt.application.spring.event.RawMarketMessageReceivedEvent;
import com.marmitt.application.spring.event.RawUserDataMessageReceivedEvent;
import com.marmitt.core.dto.events.WebSocketClosedEvent;
import com.marmitt.core.dto.events.WebSocketClosingEvent;
import com.marmitt.core.dto.events.WebSocketConnectedEvent;
import com.marmitt.core.dto.events.WebSocketFailedEvent;
import com.marmitt.core.dto.websocket.MessageContext;
import com.marmitt.core.enums.StreamChannel;
import com.marmitt.core.ports.outbound.events.EventPublisherPort;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

@Slf4j
public class OkHttp3ListenerConverter {

    private final EventPublisherPort eventPublisher;
    private final StreamChannel channel;

    public OkHttp3ListenerConverter(EventPublisherPort eventPublisher, StreamChannel channel) {
        this.eventPublisher = eventPublisher;
        this.channel = channel;
    }

    public WebSocketListener convert(String exchangeName, UUID connectionId) {
        return new WebSocketListener() {

            @Override
            public void onOpen(@NotNull WebSocket webSocket, @NotNull Response response) {
                log.info("WebSocket opened - exchange={} channel={} connectionId={}", exchangeName, channel, connectionId);
                eventPublisher.publishEvent(WebSocketConnectedEvent.of(
                        exchangeName, "Connection established successfully", connectionId));
            }

            @Override
            public void onMessage(@NotNull WebSocket webSocket, @NotNull String text) {
                log.debug("Message received - exchange={} channel={} length={}", exchangeName, channel, text.length());

                if (channel == StreamChannel.USER_DATA) {
                    MessageContext context = MessageContext.createUserData(exchangeName, connectionId);
                    eventPublisher.publishEvent(new RawUserDataMessageReceivedEvent(this, text, context));
                } else {
                    MessageContext context = MessageContext.create(exchangeName, connectionId);
                    eventPublisher.publishEvent(new RawMarketMessageReceivedEvent(this, text, context));
                }
            }

            @Override
            public void onClosing(@NotNull WebSocket webSocket, int code, @NotNull String reason) {
                log.info("WebSocket closing - exchange={} channel={} code={} reason={}", exchangeName, channel, code, reason);
                eventPublisher.publishEvent(WebSocketClosingEvent.of(exchangeName, code, reason, connectionId));
            }

            @Override
            public void onClosed(@NotNull WebSocket webSocket, int code, @NotNull String reason) {
                log.info("WebSocket closed - exchange={} channel={} code={} reason={}", exchangeName, channel, code, reason);
                eventPublisher.publishEvent(WebSocketClosedEvent.of(exchangeName, code, reason, connectionId));
            }

            @Override
            public void onFailure(@NotNull WebSocket webSocket, @NotNull Throwable throwable, Response response) {
                log.error("WebSocket failure - exchange={} channel={} connectionId={}", exchangeName, channel, connectionId, throwable);
                eventPublisher.publishEvent(WebSocketFailedEvent.of(
                        exchangeName, "Connection failed: " + throwable.getMessage(), connectionId, throwable));
            }
        };
    }
}
