package com.marmitt.controller.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record CurrencyPair(
        @NotNull(message = "Base currency must not be null")
        @NotEmpty(message = "Base currency must not be empty")
        String baseCurrency,
        
        @NotNull(message = "Quote currency must not be null")
        @NotEmpty(message = "Quote currency must not be empty")
        String quoteCurrency
) {
}