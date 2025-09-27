package com.marmitt.controller.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record OrderCancelRequest(
        @NotNull(message = "Exchange must not be null")
        @NotEmpty(message = "Exchange must not be empty")
        String exchange,

        @NotNull(message = "Order ID must not be null")
        @NotEmpty(message = "Order ID must not be empty")
        String orderId
) {
}