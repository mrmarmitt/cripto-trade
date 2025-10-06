package com.marmitt.application.spring.controller.dto.common;

import com.marmitt.core.enums.StreamType;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record CurrencyPairRequest(
        @NotNull(message = "Base currency must not be null")
        @NotEmpty(message = "Base currency must not be empty")
        String baseCurrency,

        @NotNull(message = "Quote currency must not be null")
        @NotEmpty(message = "Quote currency must not be empty")
        String quoteCurrency,

        @NotNull(message = "Stream type must not be null")
        StreamType streamType
) {
}