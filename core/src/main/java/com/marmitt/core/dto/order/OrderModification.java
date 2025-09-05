package com.marmitt.core.dto.order;

import java.math.BigDecimal;

public record OrderModification(
        BigDecimal newQuantity,
        BigDecimal newPrice,
        BigDecimal newStopPrice
) {
    public static OrderModification quantity(BigDecimal newQuantity) {
        return new OrderModification(newQuantity, null, null);
    }
    
    public static OrderModification price(BigDecimal newPrice) {
        return new OrderModification(null, newPrice, null);
    }
    
    public static OrderModification stopPrice(BigDecimal newStopPrice) {
        return new OrderModification(null, null, newStopPrice);
    }
    
    public static OrderModification quantityAndPrice(BigDecimal newQuantity, BigDecimal newPrice) {
        return new OrderModification(newQuantity, newPrice, null);
    }
    
    public boolean hasQuantityChange() {
        return newQuantity != null;
    }
    
    public boolean hasPriceChange() {
        return newPrice != null;
    }
    
    public boolean hasStopPriceChange() {
        return newStopPrice != null;
    }
}