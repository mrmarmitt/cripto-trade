package com.marmitt.coinbase.processor.send;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marmitt.core.dto.websocket.request.MessageRequest;
import com.marmitt.core.enums.MessageType;


public class CancelOrderProcessor implements CoinbaseSenderProcessor {

    private final ObjectMapper objectMapper;

    public CancelOrderProcessor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String execute(MessageRequest request) {
        return "";
    }

    @Override
    public boolean canProcess(MessageType messageType) {
        return false;
    }
}
