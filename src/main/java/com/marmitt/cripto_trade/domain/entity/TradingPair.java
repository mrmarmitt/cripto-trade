package com.marmitt.cripto_trade.domain.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Objects;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TradingPair {
    private String baseCurrency;
    private String quoteCurrency;

    public TradingPair(String symbol) {
        if (symbol == null || !symbol.contains("/")) {
            throw new IllegalArgumentException("Invalid trading pair symbol. Expected format: BASE/QUOTE");
        }
        String[] parts = symbol.split("/");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid trading pair symbol. Expected format: BASE/QUOTE");
        }
        this.baseCurrency = parts[0].toUpperCase();
        this.quoteCurrency = parts[1].toUpperCase();
    }

    public String getSymbol() {
        return baseCurrency + "/" + quoteCurrency;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TradingPair that = (TradingPair) o;
        return Objects.equals(baseCurrency, that.baseCurrency) &&
               Objects.equals(quoteCurrency, that.quoteCurrency);
    }

    @Override
    public int hashCode() {
        return Objects.hash(baseCurrency, quoteCurrency);
    }
}