package com.marmitt.core.dto.configuration;

import com.marmitt.core.enums.StreamType;

import java.util.List;
import java.util.Map;

public record WebSocketConnectionParameters(
        List<StreamType> streamType,
        List<CurrencyPair> currencyPairs,
        Map<String, Object> additionalParameters
) {

    public static WebSocketConnectionParameters of(List<StreamType> streams, List<CurrencyPair> pairs) {
        return new WebSocketConnectionParameters(streams, pairs, Map.of());
    }

    public static WebSocketConnectionParameters of(List<StreamType> streams, List<CurrencyPair> pairs, Map<String, Object> parameters) {
        return new WebSocketConnectionParameters(streams, pairs, parameters);
    }

    public List<CurrencyPair> getCurrencyPairs() {
        return currencyPairs != null ? currencyPairs : List.of();
    }

    public boolean isMultiSymbol() {
        return currencyPairs != null && currencyPairs.size() > 1;
    }

    public Object getParameterValue(String key) {
        return additionalParameters.get(key);
    }

}