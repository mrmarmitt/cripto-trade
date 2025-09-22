package com.marmitt.core.dto.websocket.request;

import com.marmitt.core.dto.common.CurrencyPair;

import java.util.List;
import java.util.Map;

public record WebSocketConnectionParametersRequest(
        List<CurrencyPair> currencyPairs,
        Map<String, Object> additionalParameters
) {

    public static WebSocketConnectionParametersRequest of(List<CurrencyPair> pairs) {
        return new WebSocketConnectionParametersRequest(pairs, Map.of());
    }

    public static WebSocketConnectionParametersRequest of(List<CurrencyPair> pairs, Map<String, Object> parameters) {
        return new WebSocketConnectionParametersRequest(pairs, parameters);
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