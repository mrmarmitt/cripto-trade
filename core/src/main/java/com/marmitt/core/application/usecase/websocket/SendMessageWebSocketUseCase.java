package com.marmitt.core.application.usecase.websocket;

import com.marmitt.core.dto.websocket.request.MessageRequest;
import com.marmitt.core.dto.wrapper.WebSocketConnectionManager;
import com.marmitt.core.ports.inbound.websocket.SendMessageWebSocketPort;
import com.marmitt.core.ports.outbound.exchange.adapter.ExchangeAdapterPort;
import com.marmitt.core.ports.outbound.exchange.adapter.SenderMessageProcessorPort;
import com.marmitt.core.ports.outbound.repository.ExchangeAdapterRepositoryPort;
import com.marmitt.core.ports.outbound.repository.WebSocketConnectionRepositoryPort;

public class SendMessageWebSocketUseCase implements SendMessageWebSocketPort {

    private final WebSocketConnectionRepositoryPort connectionRepository;
    private final ExchangeAdapterRepositoryPort adapterRepository;

    public SendMessageWebSocketUseCase(WebSocketConnectionRepositoryPort connectionRepository,
                                       ExchangeAdapterRepositoryPort adapterRepository) {
        this.connectionRepository = connectionRepository;
        this.adapterRepository = adapterRepository;
    }

    @Override
    public void execute(MessageRequest request) {
        ExchangeAdapterPort adapter = this.adapterRepository.getAdapter(request.getExchangeName());
        WebSocketConnectionManager manager = connectionRepository.getConnection(request.getExchangeName());
        SenderMessageProcessorPort senderMessageProcessor = adapter.getSenderMessageProcessor();

        manager.addRequestToHistory(request);
        String processedMessage = senderMessageProcessor.execute(request);
        adapter.getWebSocketPort().sendMessage(processedMessage);
    }
}
