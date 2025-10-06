package com.marmitt.application.spring.controller.dto.order;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record OrderNotificationStreamRequest(
        @NotNull(message = "Exchange must not be null")
        @NotEmpty(message = "Exchange must not be empty")
        String exchange
) {
}