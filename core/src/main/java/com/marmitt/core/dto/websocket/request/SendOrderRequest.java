package com.marmitt.core.dto.websocket.request;

import com.marmitt.core.enums.MessageType;
import com.marmitt.core.enums.OrderSide;
import com.marmitt.core.enums.OrderType;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
public class SendOrderRequest extends MessageRequest {
    
    private final String symbol;
    private final BigDecimal quantity;
    private final BigDecimal price;
    private final OrderType orderType;
    private final OrderSide orderSide;
    private final String clientOrderId;  // ID interno para correlação
    
    public SendOrderRequest(String exchangeName, String symbol, BigDecimal quantity, 
                           BigDecimal price, OrderType orderType, OrderSide orderSide, 
                           String clientOrderId) {
        super(exchangeName, MessageType.ORDER_PLACEMENT);
        this.symbol = symbol;
        this.quantity = quantity;
        this.price = price;
        this.orderType = orderType;
        this.orderSide = orderSide;
        this.clientOrderId = clientOrderId;
    }

}