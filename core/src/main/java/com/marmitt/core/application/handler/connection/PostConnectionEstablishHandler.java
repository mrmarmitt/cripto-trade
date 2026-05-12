package com.marmitt.core.application.handler.connection;

import com.marmitt.core.dto.connection.ConnectionKey;
import com.marmitt.core.dto.events.WebSocketConnectedEvent;
import com.marmitt.core.dto.exchange.command.PostConnectionCommandResult;
import com.marmitt.core.dto.wrapper.WebSocketConnectionManager;
import com.marmitt.core.enums.StreamChannel;
import com.marmitt.core.ports.inbound.websocket.PostConnectionEstablishedPort;
import com.marmitt.core.ports.outbound.exchange.streaming.ExchangeStreamingPort;
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
        if (event.channel() != StreamChannel.MARKET) {
            return PostConnectionCommandResult.success(event.exchange(), "Post-connection not applicable for channel " + event.channel());
        }

        Optional<ExchangeStreamingPort> streamingOptional = adapterRepository.findStreamingByName(event.exchange());
        if (streamingOptional.isEmpty()) {
            return PostConnectionCommandResult.failure(event.exchange(), "Exchange does not exist.");
        }

        ExchangeStreamingPort streaming = streamingOptional.get();
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
}
