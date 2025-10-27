package com.marmitt.core.dto.websocket.request;

import com.marmitt.core.enums.MessageType;
import lombok.Getter;

@Getter
public class SendCancelOrderRequest extends MessageRequest {
    
    private final String orderId;
    private final String symbol;
    
    public SendCancelOrderRequest(String exchangeName, String orderId, String symbol) {
        super(exchangeName, MessageType.ORDER_CANCELLATION);
        this.orderId = orderId;
        this.symbol = symbol;
    }

}