package com.marmitt.core.dto.order;

import com.marmitt.core.enums.OrderSide;
import com.marmitt.core.enums.OrderStatusType;
import com.marmitt.core.enums.OrderType;
import com.marmitt.core.domain.Symbol;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderResult(
        String orderId,
        String clientOrderId,
        Symbol symbol,
        OrderSide side,
        OrderType type,
        BigDecimal originalQuantity,
        BigDecimal executedQuantity,
        BigDecimal price,
        OrderStatusType status,
        Instant timestamp,
        String errorMessage
) {
    public static OrderResult success(String orderId, String clientOrderId, Symbol symbol, 
                                    OrderSide side, OrderType type, BigDecimal quantity, 
                                    BigDecimal price, OrderStatusType status) {
        return new OrderResult(orderId, clientOrderId, symbol, side, type, quantity, 
                              BigDecimal.ZERO, price, status, Instant.now(), null);
    }
    
    public static OrderResult error(String clientOrderId, String errorMessage) {
        return new OrderResult(null, clientOrderId, null, null, null, null, null, null, 
                              OrderStatusType.REJECTED, Instant.now(), errorMessage);
    }
    
    public boolean isSuccess() {
        return errorMessage == null;
    }
    
    public boolean hasError() {
        return errorMessage != null;
    }
}