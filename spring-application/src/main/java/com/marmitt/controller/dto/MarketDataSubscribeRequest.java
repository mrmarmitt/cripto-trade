package com.marmitt.controller.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record MarketDataSubscribeRequest(
        @NotNull(message = "Exchange must not be null")
        @NotEmpty(message = "Exchange must not be empty")
        String exchange,

        String symbol,

        @Valid
        List<CurrencyPair> symbols,

        @NotNull(message = "Subscribe flag must not be null")
        Boolean subscribe
) {
}