package com.marmitt.controller.dto;

import com.marmitt.core.enums.StreamType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record WebSocketConnectRequest(
        @NotNull(message = "Exchange must not be null")
        @NotEmpty(message = "Exchange must not be empty")
        String exchange,
        
        @NotNull(message = "Stream type must not be null")
        StreamType streamType,
        
        @Valid
        List<CurrencyPair> symbols
) {
    
    public WebSocketConnectRequest {
        if (streamType == StreamType.TICKER && (symbols == null || symbols.isEmpty())) {
            throw new IllegalArgumentException("At least one currency pair must be provided for TICKER stream type");
        }
    }
}