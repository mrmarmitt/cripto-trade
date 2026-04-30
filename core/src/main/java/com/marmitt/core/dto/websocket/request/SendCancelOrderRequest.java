package com.marmitt.core.dto.websocket.request;

import com.marmitt.core.enums.MessageType;
import lombok.Getter;

@Getter
public class SendCancelOrderRequest extends MessageRequest {

    private final String clientOrderId;
    private final String symbol;

    public SendCancelOrderRequest(String exchangeName, String clientOrderId, String symbol) {
        super(exchangeName, MessageType.ORDER_CANCELLATION);
        this.clientOrderId = clientOrderId;
        this.symbol = symbol;
    }
}
