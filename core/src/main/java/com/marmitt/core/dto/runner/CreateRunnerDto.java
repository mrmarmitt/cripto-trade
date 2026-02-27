package com.marmitt.core.dto.runner;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Set;
import java.util.UUID;

public record CreateRunnerDto(
        @NotNull(message = "Strategy ID is required")
        UUID strategyId,

        @NotBlank(message = "Symbol is required")
        String symbol,

        @NotBlank(message = "Exchange name for order execution is required")
        String exchangeName,

        Set<String> allowedMarketDataSources
) {
}
