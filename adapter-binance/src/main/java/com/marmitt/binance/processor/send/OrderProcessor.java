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
            Map<String, Object> order = new HashMap<>();
            order.put("symbol", orderRequest.getSymbol().toUpperCase());
            order.put("side", mapOrderSide(orderRequest.getOrderSide()));
            order.put("type", mapOrderType(orderRequest.getOrderType()));
            order.put("quantity", orderRequest.getQuantity().toPlainString());
            
            if (orderRequest.getPrice() != null) {
                order.put("price", orderRequest.getPrice().toPlainString());
            }
            
            order.put("timeInForce", "GTC");
            order.put("timestamp", System.currentTimeMillis());

            return objectMapper.writeValueAsString(order);
        } catch (Exception e) {
            throw new RuntimeException("Failed to process order message", e);
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