package com.marmitt.binance.processor.send;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marmitt.binance.auth.BinanceRequestSigner;
import com.marmitt.core.dto.websocket.request.MessageRequest;
import com.marmitt.core.dto.websocket.request.SendCancelOrderRequest;
import com.marmitt.core.enums.MessageType;


import java.util.HashMap;
import java.util.Map;

public class CancelOrderProcessor implements BinanceSenderProcessor {

    private final ObjectMapper objectMapper;
    private final BinanceRequestSigner signer;

    public CancelOrderProcessor(ObjectMapper objectMapper, BinanceRequestSigner signer) {
        this.objectMapper = objectMapper;
        this.signer = signer;
    }

    @Override
    public String execute(MessageRequest request) {
        if (!(request instanceof SendCancelOrderRequest cancelRequest)) {
            throw new IllegalArgumentException("Expected SendCancelOrderRequest but received: " + request.getClass().getSimpleName());
        }

        try {
            Map<String, Object> params = new HashMap<>();
            params.put("symbol", cancelRequest.getSymbol().toUpperCase());
            params.put("origClientOrderId", cancelRequest.getClientOrderId());

            Map<String, Object> message = new HashMap<>();
            message.put("id", cancelRequest.getClientOrderId());
            message.put("method", "order.cancel");
            message.put("params", signer.signWebSocketParams(params));

            return objectMapper.writeValueAsString(message);
        } catch (Exception e) {
            throw new RuntimeException("Failed to process cancel order request", e);
        }
    }

    @Override
    public boolean canProcess(MessageType messageType) {
        return MessageType.ORDER_CANCELLATION.equals(messageType);
    }
}
