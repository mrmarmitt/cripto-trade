package com.marmitt.application.spring.controller.mapper;

import com.marmitt.application.spring.controller.dto.order.OrderCancelRequest;
import com.marmitt.application.spring.controller.dto.order.OrderCreateRequest;
import com.marmitt.core.dto.websocket.request.SendOrderRequest;
import com.marmitt.core.dto.websocket.request.SendCancelOrderRequest;
import com.marmitt.core.enums.OrderSide;
import com.marmitt.core.enums.OrderType;
import jakarta.validation.Valid;

public class OrderManagementMapper {

    public static SendOrderRequest toSendOrderRequest(OrderCreateRequest request) {
        OrderSide orderSide = parseOrderSide(request.orderSide());
        OrderType orderType = parseOrderType(request.orderType());
        
        return new SendOrderRequest(
                request.exchange(),
                request.symbol(),
                request.quantity(),
                request.price(),
                orderType,
                orderSide
        );
    }
    
    public static SendCancelOrderRequest toSendCancelOrderRequest(@Valid OrderCancelRequest request) {
        return new SendCancelOrderRequest(
                request.exchange(),
                request.orderId(),
                null // symbol não é obrigatório no cancel request do controller
        );
    }
    
    private static OrderSide parseOrderSide(String orderSide) {
        return switch (orderSide.toUpperCase()) {
            case "BUY" -> OrderSide.BUY;
            case "SELL" -> OrderSide.SELL;
            default -> throw new IllegalArgumentException("Invalid order side: " + orderSide);
        };
    }
    
    private static OrderType parseOrderType(String orderType) {
        return switch (orderType.toUpperCase()) {
            case "MARKET" -> OrderType.MARKET;
            case "LIMIT" -> OrderType.LIMIT;
            case "STOP_LOSS" -> OrderType.STOP_LOSS;
            case "STOP_LOSS_LIMIT" -> OrderType.STOP_LOSS_LIMIT;
            case "TAKE_PROFIT" -> OrderType.TAKE_PROFIT;
            case "TAKE_PROFIT_LIMIT" -> OrderType.TAKE_PROFIT_LIMIT;
            default -> throw new IllegalArgumentException("Invalid order type: " + orderType);
        };
    }
}