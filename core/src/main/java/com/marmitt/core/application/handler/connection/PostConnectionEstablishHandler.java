package com.marmitt.core.application.handler.connection;

import com.marmitt.core.dto.events.WebSocketConnectedEvent;
import com.marmitt.core.dto.exchange.command.PostConnectionCommandResult;
import com.marmitt.core.dto.websocket.request.MessageRequest;
import com.marmitt.core.dto.wrapper.WebSocketConnectionManager;
import com.marmitt.core.ports.inbound.websocket.PostConnectionEstablishedPort;
import com.marmitt.core.ports.outbound.exchange.adapter.SenderMessageProcessorPort;
import com.marmitt.core.ports.outbound.exchange.streaming.ExchangeStreamingPort;
import com.marmitt.core.ports.outbound.repository.ExchangeAdapterRepositoryPort;
import com.marmitt.core.ports.outbound.repository.WebSocketConnectionRepositoryPort;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

@Slf4j
public class PostConnectionEstablishHandler implements PostConnectionEstablishedPort {

    private final WebSocketConnectionRepositoryPort connectionRepository;
    private final ExchangeAdapterRepositoryPort adapterRepository;

    public PostConnectionEstablishHandler(WebSocketConnectionRepositoryPort connectionRepository,
                                          ExchangeAdapterRepositoryPort adapterRepository) {
        this.connectionRepository = connectionRepository;
        this.adapterRepository = adapterRepository;
    }

    @Override
    public PostConnectionCommandResult execute(WebSocketConnectedEvent event) {
        Optional<ExchangeStreamingPort> streamingOptional = adapterRepository.findStreamingByName(event.exchange());
        WebSocketConnectionManager manager = connectionRepository.getConnection(event.exchange());

        if (streamingOptional.isEmpty()) {
            return PostConnectionCommandResult.failure(event.exchange(), "Exchange does not exist.");
        }

        ExchangeStreamingPort streaming = streamingOptional.get();
        if (!streaming.requiresPostConnection()) {
            return PostConnectionCommandResult.success(event.exchange(), "Post-connection not configured.");
        }

        SenderMessageProcessorPort senderMessageProcessor = streaming.getSenderMessageProcessor();
        MessageRequest lastRequestHistory = manager.getLastRequestHistory();

        try {
            String sentMessage = senderMessageProcessor.execute(lastRequestHistory);
            streaming.getWebSocketPort().sendMessage(sentMessage);

            return PostConnectionCommandResult.success(
                    event.exchange(),
                    "Post-connection errorMessage sent: " + sentMessage
            );
        } catch (Exception e) {
            log.error("Error during post-connection operations for exchange: {}", event.exchange(), e);
            return PostConnectionCommandResult.failure(event.exchange(), e.getMessage());
        }
    }
}

