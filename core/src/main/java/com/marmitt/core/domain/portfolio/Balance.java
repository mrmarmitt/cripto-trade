package com.marmitt.core.domain.portfolio;

import lombok.Getter;

import java.math.BigDecimal;
import java.util.Objects;

@Getter
public class Balance {

    private Asset available;
    private Asset invested;
    private final Asset initialCapital;
    
    public Balance(Asset initialCapital) {
        this.initialCapital = initialCapital;
        this.available = initialCapital;
        this.invested = Asset.fiat(BigDecimal.ZERO, initialCapital.currency());
    }
    
    private Balance(Asset available, Asset invested, Asset initialCapital) {
        this.available = available;
        this.invested = invested;
        this.initialCapital = initialCapital;
    }

    public static Balance withInitialCapital(Asset initialCapital) {
        return new Balance(initialCapital);
    }
    
    public void allocate(Asset amount) {
        Objects.requireNonNull(amount, "Amount cannot be null");
        
        if (amount.isPositive()) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        
        if (hasAvailableAmount(amount)) {
            throw new IllegalArgumentException("Insufficient available balance");
        }
        
        this.available = available.subtract(amount);
        this.invested = invested.add(amount);
    }
    
    public void deallocate(Asset amount) {
        Objects.requireNonNull(amount, "Amount cannot be null");
        
        if (amount.isPositive()) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        
        if (amount.amount().compareTo(invested.amount()) > 0) {
            throw new IllegalArgumentException("Cannot deallocate more than invested amount");
        }
        
        this.invested = invested.subtract(amount);
        this.available = available.add(amount);
    }
    
    public boolean hasAvailableAmount(Asset amount) {
        return amount.amount().compareTo(available.amount()) <= 0;
    }
    
    public Asset getTotal() {
        return available.add(invested);
    }
    
    public Asset getAllocated() {
        return invested;
    }
}