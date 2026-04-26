package com.marmitt.core.dto.websocket.request;

import java.util.Objects;

public record OrderCancelRequest(
        String exchange,
        String orderId
) {
    public OrderCancelRequest {
        Objects.requireNonNull(exchange, "Exchange must not be null");
        Objects.requireNonNull(orderId, "Order ID must not be null");

        if (exchange.isBlank()) {
            throw new IllegalArgumentException("Exchange must not be empty");
        }
        if (orderId.isBlank()) {
            throw new IllegalArgumentException("Order ID must not be empty");
        }
    }
}
