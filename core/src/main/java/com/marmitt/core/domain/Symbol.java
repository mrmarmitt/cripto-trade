package com.marmitt.core.domain;

public record Symbol(String value) { //TODO: pensar em substituir por um dois campos para ficar mais fácil de identificar os moedas.
    
    public Symbol {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Symbol cannot be null or empty");
        }
    }
    
    public static Symbol of(String symbol) {
        return new Symbol(symbol.toUpperCase());
    }
    
    public String getBaseAsset() {
        String[] parts = value.split("/");
        return parts.length > 0 ? parts[0] : value;
    }
    
    public String getQuoteAsset() {
        String[] parts = value.split("/");
        return parts.length > 1 ? parts[1] : null;
    }
    
    public boolean isPair() {
        return value.contains("/");
    }
    
    @Override
    public String toString() {
        return value;
    }
}