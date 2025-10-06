package com.marmitt.application.spring.controller.dto;

import com.marmitt.application.spring.controller.dto.common.CurrencyPairRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record WebSocketConnectRequest(
        @NotNull(message = "Exchange must not be null")
        @NotEmpty(message = "Exchange must not be empty")
        String exchange,

        @Valid
        List<CurrencyPairRequest> symbols
) {

}