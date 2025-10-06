package com.marmitt.core.dto.websocket.request;

import com.marmitt.core.enums.MessageType;
import com.marmitt.core.enums.OrderSide;
import com.marmitt.core.enums.OrderType;
import java.math.BigDecimal;

public class SendOrderRequest extends SendMessageRequest {
    
    private final String symbol;
    private final BigDecimal quantity;
    private final BigDecimal price;
    private final OrderType orderType;
    private final OrderSide orderSide;
    
    public SendOrderRequest(String exchangeName, String symbol, BigDecimal quantity, 
                           BigDecimal price, OrderType orderType, OrderSide orderSide) {
        super(exchangeName, MessageType.ORDER_PLACEMENT);
        this.symbol = symbol;
        this.quantity = quantity;
        this.price = price;
        this.orderType = orderType;
        this.orderSide = orderSide;
    }
    
    public String getSymbol() {
        return symbol;
    }
    
    public BigDecimal getQuantity() {
        return quantity;
    }
    
    public BigDecimal getPrice() {
        return price;
    }
    
    public OrderType getOrderType() {
        return orderType;
    }
    
    public OrderSide getOrderSide() {
        return orderSide;
    }
}