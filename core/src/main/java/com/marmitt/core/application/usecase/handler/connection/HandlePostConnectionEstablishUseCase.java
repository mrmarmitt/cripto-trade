package com.marmitt.core.application.usecase.handler.connection;

import com.marmitt.core.dto.events.WebSocketConnectedEvent;
import com.marmitt.core.dto.exchange.command.PostConnectionCommandResult;
import com.marmitt.core.dto.websocket.request.MessageRequest;
import com.marmitt.core.dto.wrapper.WebSocketConnectionManager;
import com.marmitt.core.ports.inbound.websocket.PostConnectionEstablishedPort;
import com.marmitt.core.ports.outbound.exchange.adapter.ExchangeAdapterPort;
import com.marmitt.core.ports.outbound.exchange.adapter.SenderMessageProcessorPort;
import com.marmitt.core.ports.outbound.repository.ExchangeAdapterRepositoryPort;
import com.marmitt.core.ports.outbound.repository.WebSocketConnectionRepositoryPort;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class HandlePostConnectionEstablishUseCase implements PostConnectionEstablishedPort {

    private final WebSocketConnectionRepositoryPort connectionRepository;
    private final ExchangeAdapterRepositoryPort adapterRepository;

    public HandlePostConnectionEstablishUseCase(WebSocketConnectionRepositoryPort connectionRepository,
                                                ExchangeAdapterRepositoryPort adapterRepository) {
        this.connectionRepository = connectionRepository;
        this.adapterRepository = adapterRepository;
    }

    @Override
    public PostConnectionCommandResult execute(WebSocketConnectedEvent event) {
        ExchangeAdapterPort adapter = adapterRepository.getAdapter(event.exchange());
        WebSocketConnectionManager manager = connectionRepository.getConnection(event.exchange());

        if (!adapter.requiresPostConnection()) {
            return PostConnectionCommandResult.success(
                    event.exchange(),
                    "Post-connection not configured.");
        }

        SenderMessageProcessorPort senderMessageProcessor = adapter.getSenderMessageProcessor();
        MessageRequest lastRequestHistory = manager.getLastRequestHistory();
        try {

            String sentMessage = senderMessageProcessor.execute(lastRequestHistory);
            adapter.getWebSocketPort().sendMessage(sentMessage);

            return PostConnectionCommandResult.success(
                    event.exchange(),
                    "Post-connection message sent: " + sentMessage
            );

        } catch (Exception e) {
            log.error("Error during post-connection operations for exchange: {}", event.exchange(), e);
            return PostConnectionCommandResult.failure(
                    event.exchange(),
                    e.getMessage()
            );
        }
    }
}