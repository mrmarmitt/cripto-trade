package com.marmitt.core.dto.websocket.request;

import com.marmitt.core.enums.MessageType;

public class SendCancelOrderRequest extends SendMessageRequest {
    
    private final String orderId;
    private final String symbol;
    
    public SendCancelOrderRequest(String exchangeName, String orderId, String symbol) {
        super(exchangeName, MessageType.ORDER_CANCELLATION);
        this.orderId = orderId;
        this.symbol = symbol;
    }
    
    public String getOrderId() {
        return orderId;
    }
    
    public String getSymbol() {
        return symbol;
    }
}