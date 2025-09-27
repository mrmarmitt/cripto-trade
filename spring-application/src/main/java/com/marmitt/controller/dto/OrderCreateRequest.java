package com.marmitt.controller.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record OrderCreateRequest(
        @NotNull(message = "Exchange must not be null")
        @NotEmpty(message = "Exchange must not be empty")
        String exchange,

        @NotNull(message = "Symbol must not be null")
        @NotEmpty(message = "Symbol must not be empty")
        String symbol,

        @NotNull(message = "Quantity must not be null")
        BigDecimal quantity,

        BigDecimal price,

        @NotNull(message = "Order side must not be null")
        @NotEmpty(message = "Order side must not be empty")
        String orderSide,

        @NotNull(message = "Order type must not be null")
        @NotEmpty(message = "Order type must not be empty")
        String orderType
) {
}