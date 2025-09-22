package com.marmitt.core.application.usecase.websocket;

import com.marmitt.core.dto.websocket.request.SendMessageRequest;
import com.marmitt.core.ports.inbound.websocket.SendMessageWebSocketPort;
import com.marmitt.core.ports.outbound.exchange.adapter.ExchangeAdapterPort;
import com.marmitt.core.ports.outbound.exchange.adapter.SenderMessageProcessorPort;
import com.marmitt.core.ports.outbound.repository.ExchangeAdapterRepositoryPort;

public class SendMessageWebSocketUseCase implements SendMessageWebSocketPort {

    private final ExchangeAdapterRepositoryPort adapterRepository;

    public SendMessageWebSocketUseCase(ExchangeAdapterRepositoryPort adapterRepository) {
        this.adapterRepository = adapterRepository;
    }

    @Override
    public void execute(SendMessageRequest request) {
        ExchangeAdapterPort adapter = this.adapterRepository.getAdapter(request.getExchangeName());
        SenderMessageProcessorPort senderMessageProcessor = adapter.getSenderMessageProcessor();

        String processedMessage = senderMessageProcessor.execute(request);
        adapter.getWebSocketPort().sendMessage(processedMessage);
    }
}
