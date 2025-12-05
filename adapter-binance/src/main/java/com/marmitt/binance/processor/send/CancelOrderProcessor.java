package com.marmitt.binance.processor.send;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marmitt.core.dto.websocket.request.SendCancelOrderRequest;
import com.marmitt.core.dto.websocket.request.MessageRequest;
import com.marmitt.core.enums.MessageType;
import com.marmitt.core.ports.outbound.exchange.adapter.SenderSpecializedProcessorPort;

import java.util.HashMap;
import java.util.Map;

public class CancelOrderProcessor implements SenderSpecializedProcessorPort {

    private final ObjectMapper objectMapper;

    public CancelOrderProcessor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String execute(MessageRequest request) {
        if (!(request instanceof SendCancelOrderRequest cancelRequest)) {
            throw new IllegalArgumentException("Expected SendCancelOrderRequest but received: " + request.getClass().getSimpleName());
        }

        try {
            Map<String, Object> cancelOrder = new HashMap<>();
            cancelOrder.put("currency", cancelRequest.getSymbol().toUpperCase());
            cancelOrder.put("orderId", cancelRequest.getOrderId());
            cancelOrder.put("timestamp", System.currentTimeMillis());

            return objectMapper.writeValueAsString(cancelOrder);
        } catch (Exception e) {
            throw new RuntimeException("Failed to process cancel order errorMessage", e);
        }
    }

    @Override
    public boolean canProcess(MessageType messageType) {
        return MessageType.ORDER_CANCELLATION.equals(messageType);
    }
}