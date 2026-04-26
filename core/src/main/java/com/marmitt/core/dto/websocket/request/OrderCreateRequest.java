package com.marmitt.core.dto.websocket.request;

import java.math.BigDecimal;
import java.util.Objects;

public record OrderCreateRequest(
        String exchange,
        String symbol,
        BigDecimal quantity,
        BigDecimal price,
        String orderSide,
        String orderType
) {
    public OrderCreateRequest {
        Objects.requireNonNull(exchange, "Exchange must not be null");
        Objects.requireNonNull(symbol, "Symbol must not be null");
        Objects.requireNonNull(quantity, "Quantity must not be null");
        Objects.requireNonNull(orderSide, "Order side must not be null");
        Objects.requireNonNull(orderType, "Order type must not be null");

        if (exchange.isBlank()) {
            throw new IllegalArgumentException("Exchange must not be empty");
        }
        if (symbol.isBlank()) {
            throw new IllegalArgumentException("Symbol must not be empty");
        }
        if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        if (price != null && price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Price must be positive when provided");
        }
        if (orderSide.isBlank()) {
            throw new IllegalArgumentException("Order side must not be empty");
        }
        if (orderType.isBlank()) {
            throw new IllegalArgumentException("Order type must not be empty");
        }
    }
}
