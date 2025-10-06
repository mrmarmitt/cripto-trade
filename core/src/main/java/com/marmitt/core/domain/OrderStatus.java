package com.marmitt.core.domain;

import com.marmitt.core.enums.OrderSide;
import com.marmitt.core.enums.OrderStatusType;
import com.marmitt.core.enums.OrderType;
import java.math.BigDecimal;
import java.time.Instant;

public record OrderStatus(
        String orderId,
        String clientOrderId,
        Symbol symbol,
        OrderSide side,
        OrderType type,
        BigDecimal originalQuantity,
        BigDecimal executedQuantity,
        BigDecimal remainingQuantity,
        BigDecimal price,
        BigDecimal averagePrice,
        OrderStatusType status,
        Instant createdTime,
        Instant updatedTime
) {
    public boolean isFilled() {
        return OrderStatusType.FILLED.equals(status);
    }
    
    public boolean isPartiallyFilled() {
        return OrderStatusType.PARTIALLY_FILLED.equals(status);
    }
    
    public boolean isCancelled() {
        return OrderStatusType.CANCELLED.equals(status);
    }
    
    public boolean isRejected() {
        return OrderStatusType.REJECTED.equals(status);
    }
    
    public boolean isActive() {
        return OrderStatusType.NEW.equals(status) || OrderStatusType.PARTIALLY_FILLED.equals(status);
    }
    
    public BigDecimal getExecutionProgress() {
        if (originalQuantity == null || originalQuantity.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return executedQuantity.divide(originalQuantity, 4, java.math.RoundingMode.HALF_UP);
    }
}