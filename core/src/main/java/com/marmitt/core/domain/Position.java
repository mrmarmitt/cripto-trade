package com.marmitt.core.domain;

import com.marmitt.core.enums.PositionSide;
import java.math.BigDecimal;
import java.time.Instant;

public record Position(
        Symbol symbol,
        BigDecimal quantity,
        BigDecimal averagePrice,
        BigDecimal currentPrice,
        PositionSide side,
        Instant openTime,
        Instant lastUpdated
) {
    public BigDecimal getCurrentValue() {
        return quantity.multiply(currentPrice != null ? currentPrice : BigDecimal.ZERO);
    }
    
    public BigDecimal getUnrealizedPnL() {
        if (currentPrice == null || averagePrice == null) {
            return BigDecimal.ZERO;
        }
        
        BigDecimal priceDiff = currentPrice.subtract(averagePrice);
        if (side == PositionSide.SHORT) {
            priceDiff = priceDiff.negate();
        }
        
        return priceDiff.multiply(quantity);
    }
    
    public BigDecimal getUnrealizedPnLPercent() {
        if (averagePrice == null || averagePrice.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        
        return getUnrealizedPnL()
                .divide(quantity.multiply(averagePrice), 4, java.math.RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }
    
    public boolean isLong() {
        return PositionSide.LONG.equals(side);
    }
    
    public boolean isShort() {
        return PositionSide.SHORT.equals(side);
    }
    
    public boolean isProfitable() {
        return getUnrealizedPnL().compareTo(BigDecimal.ZERO) > 0;
    }
}