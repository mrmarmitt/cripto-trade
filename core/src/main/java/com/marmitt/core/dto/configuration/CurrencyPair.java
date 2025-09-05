package com.marmitt.core.dto.configuration;

public record CurrencyPair(
        String baseCurrency,
        String quoteCurrency
) {
}