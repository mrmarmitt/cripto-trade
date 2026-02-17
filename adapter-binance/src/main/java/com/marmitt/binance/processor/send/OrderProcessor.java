package com.marmitt.binance.processor.send;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marmitt.core.dto.websocket.request.MessageRequest;
import com.marmitt.core.dto.websocket.request.SendOrderRequest;
import com.marmitt.core.enums.MessageType;
import com.marmitt.core.enums.OrderSide;
import com.marmitt.core.enums.OrderType;
import com.marmitt.core.ports.outbound.exchange.adapter.SenderSpecializedProcessorPort;

import java.util.HashMap;
import java.util.Map;

public class OrderProcessor implements SenderSpecializedProcessorPort {

    private final ObjectMapper objectMapper;

    public OrderProcessor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String execute(MessageRequest request) {
        if (!(request instanceof SendOrderRequest orderRequest)) {
            throw new IllegalArgumentException("Expected SendOrderRequest but received: " + request.getClass().getSimpleName());
        }

        try {
            Map<String, Object> message = new HashMap<>();
            message.put("id", orderRequest.getClientOrderId());  // Correlation ID
            message.put("method", "order.place");
            
            // Parâmetros da ordem dentro de "params"
            Map<String, Object> params = new HashMap<>();
            params.put("symbol", orderRequest.getSymbol().toUpperCase());
            params.put("side", mapOrderSide(orderRequest.getOrderSide()));
            params.put("type", mapOrderType(orderRequest.getOrderType()));
            params.put("quantity", orderRequest.getQuantity().toPlainString());
            params.put("newClientOrderId", orderRequest.getClientOrderId());
            params.put("timestamp", System.currentTimeMillis());
            
            // Adicionar preço para ordens LIMIT
            if (orderRequest.getPrice() != null && 
                (orderRequest.getOrderType() == OrderType.LIMIT || 
                 orderRequest.getOrderType() == OrderType.STOP_LOSS_LIMIT ||
                 orderRequest.getOrderType() == OrderType.TAKE_PROFIT_LIMIT)) {
                params.put("price", orderRequest.getPrice().toPlainString());
            }
            
            // TimeInForce obrigatório para ordens LIMIT
            if (orderRequest.getOrderType() == OrderType.LIMIT ||
                orderRequest.getOrderType() == OrderType.STOP_LOSS_LIMIT ||
                orderRequest.getOrderType() == OrderType.TAKE_PROFIT_LIMIT) {
                params.put("timeInForce", "GTC");
            }
            
            // TODO: Adicionar apiKey e signature para autenticação
            // params.put("apiKey", "your-api-key");
            // params.put("signature", generateSignature(params));
            
            message.put("params", params);

            return objectMapper.writeValueAsString(message);
        } catch (Exception e) {
            throw new RuntimeException("Failed to process order errorMessage", e);
        }
    }

    @Override
    public boolean canProcess(MessageType messageType) {
        return MessageType.ORDER_PLACEMENT.equals(messageType);
    }

    private String mapOrderSide(OrderSide orderSide) {
        return switch (orderSide) {
            case BUY -> "BUY";
            case SELL -> "SELL";
        };
    }

    private String mapOrderType(OrderType orderType) {
        return switch (orderType) {
            case MARKET -> "MARKET";
            case LIMIT -> "LIMIT";
            case STOP_LOSS -> "STOP_LOSS";
            case STOP_LOSS_LIMIT -> "STOP_LOSS_LIMIT";
            case TAKE_PROFIT -> "TAKE_PROFIT";
            case TAKE_PROFIT_LIMIT -> "TAKE_PROFIT_LIMIT";
        };
    }
}