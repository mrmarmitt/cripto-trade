package com.marmitt.core.domain.portfolio;

import com.marmitt.core.enums.AssetType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public record Asset(
        BigDecimal amount,
        String currency,
        AssetType type
) {
    
    public Asset {
        Objects.requireNonNull(amount, "Amount cannot be null");
        Objects.requireNonNull(currency, "Currency cannot be null");
        Objects.requireNonNull(type, "AssetType cannot be null");
        
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Amount cannot be negative");
        }
        
        if (currency.isBlank()) {
            throw new IllegalArgumentException("Currency cannot be blank");
        }
    }
    
    public static Asset crypto(BigDecimal amount, String currency) {
        return new Asset(amount, currency.toUpperCase(), AssetType.CRYPTOCURRENCY);
    }
    
    public static Asset fiat(BigDecimal amount, String currency) {
        return new Asset(amount, currency.toUpperCase(), AssetType.FIAT);
    }
    
    public static Asset stableCoin(BigDecimal amount, String currency) {
        return new Asset(amount, currency.toUpperCase(), AssetType.STABLECOIN);
    }
    
    /**
     * Factory method que usa TradingAssetsConfig para determinar tipo automaticamente
     */
    public static Asset of(BigDecimal amount, String currency) {
        AssetType type = TradingAssetsConfig.classifyAssetType(currency);
        
        if (type == AssetType.NOT_SUPPORTED) {
            throw new IllegalArgumentException(
                "Unsupported currency: " + currency + 
                ". Add it to TradingAssetsConfig or use supported currencies only."
            );
        }
        
        return new Asset(amount, currency.toUpperCase(), type);
    }
    
    public Asset add(Asset other) {
        if (!this.currency.equals(other.currency)) {
            throw new IllegalArgumentException("Cannot add assets with different currencies");
        }
        return new Asset(this.amount.add(other.amount), this.currency, this.type);
    }
    
    public Asset subtract(Asset other) {
        if (!this.currency.equals(other.currency)) {
            throw new IllegalArgumentException("Cannot subtract assets with different currencies");
        }
        BigDecimal result = this.amount.subtract(other.amount);
        return new Asset(result, this.currency, this.type);
    }
    
    public Asset multiply(BigDecimal multiplier) {
        return new Asset(this.amount.multiply(multiplier), this.currency, this.type);
    }
    
    public Asset divide(BigDecimal divisor) {
        return new Asset(this.amount.divide(divisor, 8, RoundingMode.HALF_UP), this.currency, this.type);
    }
    
    public boolean isZero() {
        return amount.compareTo(BigDecimal.ZERO) == 0;
    }
    
    public boolean isPositive() {
        return amount.compareTo(BigDecimal.ZERO) > 0;
    }
}