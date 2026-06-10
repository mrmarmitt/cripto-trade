package com.marmitt.core.application.handler.connection;

import com.marmitt.core.dto.connection.ConnectionKey;
import com.marmitt.core.dto.events.WebSocketConnectedEvent;
import com.marmitt.core.dto.exchange.command.PostConnectionCommandResult;
import com.marmitt.core.dto.wrapper.WebSocketConnectionManager;
import com.marmitt.core.enums.StreamChannel;
import com.marmitt.core.ports.inbound.websocket.PostConnectionEstablishedPort;
import com.marmitt.core.ports.outbound.exchange.ExchangeAdapterDescriptor;
import com.marmitt.core.ports.outbound.exchange.streaming.ExchangeStreamingPort;
import com.marmitt.core.ports.outbound.exchange.streaming.UserStreamSession;
import com.marmitt.core.ports.outbound.repository.ExchangeAdapterRepositoryPort;
import com.marmitt.core.ports.outbound.repository.WebSocketConnectionRepositoryPort;
import com.marmitt.core.ports.outbound.websocket.WebSocketPort;
import com.marmitt.core.ports.outbound.websocket.WebSocketPortRegistryPort;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

@Slf4j
public class PostConnectionEstablishHandler implements PostConnectionEstablishedPort {

    private final WebSocketConnectionRepositoryPort connectionRepository;
    private final ExchangeAdapterRepositoryPort adapterRepository;
    private final WebSocketPortRegistryPort webSocketRegistry;

    public PostConnectionEstablishHandler(WebSocketConnectionRepositoryPort connectionRepository,
                                          ExchangeAdapterRepositoryPort adapterRepository,
                                          WebSocketPortRegistryPort webSocketRegistry) {
        this.connectionRepository = connectionRepository;
        this.adapterRepository = adapterRepository;
        this.webSocketRegistry = webSocketRegistry;
    }

    @Override
    public PostConnectionCommandResult execute(WebSocketConnectedEvent event) {
        if (event.channel() == StreamChannel.MARKET) {
            return handleMarketPostConnection(event);
        }
        if (event.channel() == StreamChannel.USER_DATA) {
            return handleUserDataPostConnection(event);
        }
        return PostConnectionCommandResult.success(event.exchange(), "Post-connection not applicable for channel " + event.channel());
    }

    private PostConnectionCommandResult handleMarketPostConnection(WebSocketConnectedEvent event) {
        Optional<ExchangeAdapterDescriptor> adapterOpt = adapterRepository.findAdapter(event.exchange());
        if (adapterOpt.isEmpty()) {
            return PostConnectionCommandResult.failure(event.exchange(), "Exchange does not exist.");
        }

        ExchangeStreamingPort streaming = adapterOpt.get().streaming();
        if (!streaming.requiresPostConnection()) {
            return PostConnectionCommandResult.success(event.exchange(), "Post-connection not configured.");
        }

        ConnectionKey key = ConnectionKey.market(event.exchange());
        WebSocketConnectionManager manager = connectionRepository.getConnection(key);

        try {
            String message = streaming.formatMessage(manager.getLastRequestHistory());
            WebSocketPort webSocket = webSocketRegistry.findByExchangeName(event.exchange())
                    .orElseThrow(() -> new IllegalStateException("No WebSocket registered for exchange: " + event.exchange()));
            webSocket.sendMessage(message);
            return PostConnectionCommandResult.success(event.exchange(), "Post-connection message sent.");
        } catch (Exception e) {
            log.error("Error during post-connection operations for exchange={}", event.exchange(), e);
            return PostConnectionCommandResult.failure(event.exchange(), e.getMessage());
        }
    }

    private PostConnectionCommandResult handleUserDataPostConnection(WebSocketConnectedEvent event) {
        Optional<UserStreamSession> sessionOpt = adapterRepository.findActiveSession(event.connectionId());
        if (sessionOpt.isEmpty()) {
            log.warn("No active session found for USER_DATA connection exchange={} connectionId={}", event.exchange(), event.connectionId());
            return PostConnectionCommandResult.failure(event.exchange(), "No active user stream session.");
        }

        Optional<String> messageOpt = sessionOpt.get().subscriptionMessage();
        if (messageOpt.isEmpty()) {
            log.warn("subscriptionMessage() returned empty for exchange={} — stream will be unauthenticated", event.exchange());
            return PostConnectionCommandResult.failure(event.exchange(), "Subscription message generation failed.");
        }

        try {
            WebSocketPort webSocket = webSocketRegistry.findUserStreamByExchangeName(event.exchange())
                    .orElseThrow(() -> new IllegalStateException("No user stream WebSocket for exchange: " + event.exchange()));
            webSocket.sendMessage(messageOpt.get());
            return PostConnectionCommandResult.success(event.exchange(), "User data stream subscription message sent.");
        } catch (Exception e) {
            log.error("Error sending user data stream subscription for exchange={}", event.exchange(), e);
            return PostConnectionCommandResult.failure(event.exchange(), e.getMessage());
        }
    }
}
