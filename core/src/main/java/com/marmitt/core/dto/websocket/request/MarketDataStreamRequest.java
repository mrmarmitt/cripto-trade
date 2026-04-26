package com.marmitt.core.dto.websocket.request;

import java.util.List;
import java.util.Objects;

public record MarketDataStreamRequest(
        String exchange,
        List<CurrencyPairRequest> symbols
) {
    public MarketDataStreamRequest {
        Objects.requireNonNull(exchange, "Exchange must not be null");
        Objects.requireNonNull(symbols, "Symbols must not be null");

        if (exchange.isBlank()) {
            throw new IllegalArgumentException("Exchange must not be empty");
        }

        symbols = List.copyOf(symbols);
    }
}
