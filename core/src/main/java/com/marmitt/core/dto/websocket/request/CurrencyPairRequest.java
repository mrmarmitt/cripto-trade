package com.marmitt.core.dto.websocket.request;

import com.marmitt.core.enums.StreamType;

import java.util.Objects;

public record CurrencyPairRequest(
        String baseCurrency,
        String quoteCurrency,
        StreamType streamType
) {
    public CurrencyPairRequest {
        Objects.requireNonNull(baseCurrency, "Base currency must not be null");
        Objects.requireNonNull(quoteCurrency, "Quote currency must not be null");
        Objects.requireNonNull(streamType, "Stream type must not be null");

        if (baseCurrency.isBlank()) {
            throw new IllegalArgumentException("Base currency must not be empty");
        }
        if (quoteCurrency.isBlank()) {
            throw new IllegalArgumentException("Quote currency must not be empty");
        }
    }
}
