package com.marmitt.core.dto.websocket.request;

import java.util.Objects;

public record OrderNotificationStreamRequest(
        String exchange
) {
    public OrderNotificationStreamRequest {
        Objects.requireNonNull(exchange, "Exchange must not be null");

        if (exchange.isBlank()) {
            throw new IllegalArgumentException("Exchange must not be empty");
        }
    }
}
