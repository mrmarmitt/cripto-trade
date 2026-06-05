package com.marmitt.binance.filters;

public class SymbolFilterLoadException extends RuntimeException {

    public SymbolFilterLoadException(String symbol, String reason) {
        super("Failed to load symbol filters for " + symbol + ": " + reason);
    }

    public SymbolFilterLoadException(String symbol, String reason, Throwable cause) {
        super("Failed to load symbol filters for " + symbol + ": " + reason, cause);
    }
}
