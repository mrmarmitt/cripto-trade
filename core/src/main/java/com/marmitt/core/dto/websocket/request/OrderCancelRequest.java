package com.marmitt.core.dto.websocket.request;

import java.util.Objects;

public record OrderCancelRequest(
        String exchange,
        String symbol,
        String clientOrderId
) {
    public OrderCancelRequest {
        Objects.requireNonNull(exchange, "Exchange must not be null");
        Objects.requireNonNull(symbol, "Symbol must not be null");
        Objects.requireNonNull(clientOrderId, "Client order ID must not be null");

        if (exchange.isBlank()) throw new IllegalArgumentException("Exchange must not be empty");
        if (symbol.isBlank()) throw new IllegalArgumentException("Symbol must not be empty");
        if (clientOrderId.isBlank()) throw new IllegalArgumentException("Client order ID must not be empty");
    }
}
