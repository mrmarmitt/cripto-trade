package com.marmitt.core.dto.common;

import com.marmitt.core.enums.StreamType;

public record CurrencyPair(
        String baseCurrency,
        String quoteCurrency,
        StreamType streamType
) {
}