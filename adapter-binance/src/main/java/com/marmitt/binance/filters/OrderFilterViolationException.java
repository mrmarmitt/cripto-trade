package com.marmitt.binance.filters;

public class OrderFilterViolationException extends RuntimeException {

    public OrderFilterViolationException(String message) {
        super(message);
    }
}
