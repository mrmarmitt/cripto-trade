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

        @Valid
        List<CurrencyPair> symbols
) {

}