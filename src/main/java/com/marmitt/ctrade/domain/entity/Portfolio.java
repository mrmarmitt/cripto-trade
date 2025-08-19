package com.marmitt.ctrade.domain.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;
import java.util.HashMap;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Portfolio {
    private Map<String, BigDecimal> holdings = new HashMap<>();
    private BigDecimal totalValue = BigDecimal.ZERO;
    
    public Portfolio(Map<String, BigDecimal> holdings) {
        this.holdings = new HashMap<>(holdings);
        this.totalValue = BigDecimal.ZERO;
    }

    public BigDecimal getBalance(String currency) {
        return holdings.getOrDefault(currency, BigDecimal.ZERO);
    }

    public void updateBalance(String currency, BigDecimal amount) {
        holdings.put(currency, amount);
    }

    public boolean hasBalance(String currency, BigDecimal requiredAmount) {
        BigDecimal currentBalance = getBalance(currency);
        return currentBalance.compareTo(requiredAmount) >= 0;
    }

    public void addToBalance(String currency, BigDecimal amount) {
        BigDecimal currentBalance = getBalance(currency);
        holdings.put(currency, currentBalance.add(amount));
    }

    public void subtractFromBalance(String currency, BigDecimal amount) {
        BigDecimal currentBalance = getBalance(currency);
        BigDecimal newBalance = currentBalance.subtract(amount);
        if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Insufficient balance for currency: " + currency);
        }
        holdings.put(currency, newBalance);
    }
}