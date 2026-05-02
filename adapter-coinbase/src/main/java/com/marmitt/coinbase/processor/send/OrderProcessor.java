package com.marmitt.coinbase.processor.send;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marmitt.core.dto.websocket.request.MessageRequest;
import com.marmitt.core.dto.websocket.request.SendOrderRequest;
import com.marmitt.core.enums.MessageType;


public class OrderProcessor implements CoinbaseSenderProcessor {

    private final ObjectMapper objectMapper;

    public OrderProcessor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String execute(MessageRequest request) {
        if (!(request instanceof SendOrderRequest orderRequest)) {
            throw new IllegalArgumentException("Expected SendOrderRequest but received: " + request.getClass().getSimpleName());
        }

        // TODO: Implementar formato específico da Coinbase Advanced Trade WebSocket API
        // Por enquanto, retorna placeholder até implementarmos Coinbase
        return "{}"; // Placeholder - Coinbase Advanced Trade API format needed
    }

    @Override
    public boolean canProcess(MessageType messageType) {
        return MessageType.ORDER_PLACEMENT.equals(messageType);
    }
}
