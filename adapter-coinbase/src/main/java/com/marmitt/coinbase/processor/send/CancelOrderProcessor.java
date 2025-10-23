package com.marmitt.coinbase.processor.send;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marmitt.core.dto.websocket.request.MessageRequest;
import com.marmitt.core.enums.MessageType;
import com.marmitt.core.ports.outbound.exchange.adapter.SenderSpecializedProcessorPort;

public class CancelOrderProcessor implements SenderSpecializedProcessorPort {

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
