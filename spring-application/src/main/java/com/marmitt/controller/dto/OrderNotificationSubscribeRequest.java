package com.marmitt.controller.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record OrderNotificationSubscribeRequest(
        @NotNull(message = "Exchange must not be null")
        @NotEmpty(message = "Exchange must not be empty")
        String exchange,

        @NotNull(message = "Subscribe flag must not be null")
        Boolean subscribe
) {
}