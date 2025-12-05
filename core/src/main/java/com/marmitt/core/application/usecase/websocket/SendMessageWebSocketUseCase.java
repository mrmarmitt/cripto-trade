package com.marmitt.core.application.usecase.websocket;

import com.marmitt.core.dto.websocket.request.MessageRequest;
import com.marmitt.core.dto.websocket.response.SendWebSocketResponse;
import com.marmitt.core.dto.wrapper.WebSocketConnectionManager;
import com.marmitt.core.ports.inbound.websocket.SendMessageWebSocketPort;
import com.marmitt.core.ports.outbound.exchange.adapter.ExchangeAdapterPort;
import com.marmitt.core.ports.outbound.exchange.adapter.SenderMessageProcessorPort;
import com.marmitt.core.ports.outbound.repository.ExchangeAdapterRepositoryPort;
import com.marmitt.core.ports.outbound.repository.WebSocketConnectionRepositoryPort;

import java.util.Optional;

public class SendMessageWebSocketUseCase implements SendMessageWebSocketPort {

    private final WebSocketConnectionRepositoryPort connectionRepository;
    private final ExchangeAdapterRepositoryPort adapterRepository;

    public SendMessageWebSocketUseCase(WebSocketConnectionRepositoryPort connectionRepository,
                                       ExchangeAdapterRepositoryPort adapterRepository) {
        this.connectionRepository = connectionRepository;
        this.adapterRepository = adapterRepository;
    }

    @Override
    public SendWebSocketResponse execute(MessageRequest request) {
        WebSocketConnectionManager manager = connectionRepository.getConnection(request.getExchangeName());

        Optional<ExchangeAdapterPort> adapterOptional = adapterRepository.findByName(request.getExchangeName());
        if (adapterOptional.isEmpty()) {
            return SendWebSocketResponse.failure(request.getExchangeName(), "Exchange does not exist");
        }

        manager.addRequestToHistory(request);

        ExchangeAdapterPort adapter = adapterOptional.get();
        SenderMessageProcessorPort senderMessageProcessor = adapter.getSenderMessageProcessor();
        String processedMessage = senderMessageProcessor.execute(request);
        adapter.getWebSocketPort().sendMessage(processedMessage);

        return SendWebSocketResponse.success(request.getExchangeName());
    }
}
