package com.marmitt.core.domain.value;

import com.marmitt.core.enums.AssetType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public record Asset(
        BigDecimal amount,
        String symbol,
        AssetType type
) {
    
    public Asset {
        Objects.requireNonNull(amount, "Amount cannot be null");
        Objects.requireNonNull(symbol, "Symbol cannot be null");
        Objects.requireNonNull(type, "AssetType cannot be null");
        
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Amount cannot be negative");
        }
        
        if (symbol.isBlank()) {
            throw new IllegalArgumentException("Symbol cannot be blank");
        }
    }
    
    public static Asset crypto(BigDecimal amount, String symbol) {
        return new Asset(amount, symbol.toUpperCase(), AssetType.CRYPTOCURRENCY);
    }
    
    public static Asset fiat(BigDecimal amount, String symbol) {
        return new Asset(amount, symbol.toUpperCase(), AssetType.FIAT);
    }
    
    public static Asset stableCoin(BigDecimal amount, String symbol) {
        return new Asset(amount, symbol.toUpperCase(), AssetType.STABLECOIN);
    }
    
    public Asset add(Asset other) {
        if (!this.symbol.equals(other.symbol)) {
            throw new IllegalArgumentException("Cannot add assets with different symbols");
        }
        return new Asset(this.amount.add(other.amount), this.symbol, this.type);
    }
    
    public Asset subtract(Asset other) {
        if (!this.symbol.equals(other.symbol)) {
            throw new IllegalArgumentException("Cannot subtract assets with different symbols");
        }
        BigDecimal result = this.amount.subtract(other.amount);
        return new Asset(result, this.symbol, this.type);
    }
    
    public Asset multiply(BigDecimal multiplier) {
        return new Asset(this.amount.multiply(multiplier), this.symbol, this.type);
    }
    
    public Asset divide(BigDecimal divisor) {
        return new Asset(this.amount.divide(divisor, 8, RoundingMode.HALF_UP), this.symbol, this.type);
    }
    
    public boolean isZero() {
        return amount.compareTo(BigDecimal.ZERO) == 0;
    }
    
    public boolean isPositive() {
        return amount.compareTo(BigDecimal.ZERO) <= 0;
    }
}