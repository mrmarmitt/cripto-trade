package com.marmitt.core.domain.portfolio;

import com.marmitt.core.domain.Symbol;
import lombok.Getter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

@Getter
public class Position {

    // Getters
    private final Symbol symbol;
    private Asset quantity;
    private Asset averagePrice;
    private Asset currentPrice;
    
    public Position(Symbol symbol, Asset quantity, Asset averagePrice) {
        this.symbol = Objects.requireNonNull(symbol, "Symbol cannot be null");
        this.quantity = Objects.requireNonNull(quantity, "Quantity cannot be null");
        this.averagePrice = Objects.requireNonNull(averagePrice, "Average price cannot be null");
        this.currentPrice = averagePrice; // Initially set to average price
        
        validatePosition();
    }
    
    private void validatePosition() {
        if (quantity.amount().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Quantity cannot be negative");
        }
        
        if (!averagePrice.isPositive()) {
            throw new IllegalArgumentException("Average price must be positive");
        }
    }
    
    public static Position create(Symbol symbol, Asset quantity, Asset price) {
        return new Position(symbol, quantity, price);
    }
    
    // Business methods
    public void updatePosition(Asset additionalQuantity, Asset price) {
        Objects.requireNonNull(additionalQuantity, "Additional quantity cannot be null");
        Objects.requireNonNull(price, "Price cannot be null");
        
        if (!additionalQuantity.isPositive()) {
            throw new IllegalArgumentException("Additional quantity must be positive");
        }
        
        // Calculate new average price using weighted average
        Asset currentValue = quantity.multiply(averagePrice.amount());
        Asset additionalValue = additionalQuantity.multiply(price.amount());
        Asset totalValue = currentValue.add(additionalValue);
        
        Asset newQuantity = quantity.add(additionalQuantity);
        Asset newAveragePrice = totalValue.divide(newQuantity.amount());
        
        this.quantity = newQuantity;
        this.averagePrice = newAveragePrice;
    }
    
    public void reducePosition(Asset reduceQuantity) {
        Objects.requireNonNull(reduceQuantity, "Reduce quantity cannot be null");
        
        if (!reduceQuantity.isPositive()) {
            throw new IllegalArgumentException("Reduce quantity must be positive");
        }
        
        if (reduceQuantity.amount().compareTo(quantity.amount()) > 0) {
            throw new IllegalArgumentException("Cannot reduce more than current quantity");
        }
        
        this.quantity = quantity.subtract(reduceQuantity);
    }
    
    public void updateCurrentPrice(Asset newPrice) {
        Objects.requireNonNull(newPrice, "New price cannot be null");
        
        if (!newPrice.isPositive()) {
            throw new IllegalArgumentException("Price must be positive");
        }
        
        this.currentPrice = newPrice;
    }
    
    // Calculated values
    public Asset getCurrentValue() {
        return quantity.multiply(currentPrice.amount());
    }
    
    public Asset getUnrealizedPnL() {
        Asset currentValue = getCurrentValue();
        Asset costBasis = quantity.multiply(averagePrice.amount());
        return currentValue.subtract(costBasis);
    }
    
    public Asset getUnrealizedPnLPercentage() {
        Asset costBasis = quantity.multiply(averagePrice.amount());
        if (costBasis.isZero()) {
            return Asset.fiat(BigDecimal.ZERO, costBasis.currency());
        }
        
        Asset pnl = getUnrealizedPnL();
        BigDecimal percentage = pnl.amount().divide(costBasis.amount(), 4, RoundingMode.HALF_UP);
        return Asset.fiat(percentage.multiply(BigDecimal.valueOf(100)), "%");
    }
    
    public boolean isEmpty() {
        return quantity.isZero();
    }
    
    public boolean canSell(Asset sellQuantity) {
        return sellQuantity.amount().compareTo(quantity.amount()) <= 0;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Position position = (Position) obj;
        return Objects.equals(symbol, position.symbol);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(symbol);
    }
    
    @Override
    public String toString() {
        return String.format("%s: %s at avg %s (current %s) - P&L: %s",
                symbol.value(), quantity, averagePrice, currentPrice, getUnrealizedPnL());
    }
}