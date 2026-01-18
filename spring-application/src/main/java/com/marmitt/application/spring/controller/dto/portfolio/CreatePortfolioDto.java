package com.marmitt.application.spring.controller.dto.portfolio;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

/**
 * DTO para criação de portfolio via REST API
 */
@Builder
public record CreatePortfolioDto(

        @NotBlank(message = "Portfolio name is required")
        String name,

        @NotNull(message = "Strategy ID is required")
        UUID strategyId,

        @NotBlank(message = "Symbol is required")
        String symbol,

        @NotNull(message = "Initial capital amount is required")
        @DecimalMin(value = "0.01", message = "Initial capital must be greater than 0")
        BigDecimal initialCapitalAmount,

        @NotBlank(message = "Currency is required")
        String currency,

        @NotBlank(message = "Exchange name for order execution is required")
        String orderExecutionExchange,

        // Opcional: Se null ou vazio, aceita market data de todas as exchanges
        Set<String> allowedMarketDataSources
) {}
