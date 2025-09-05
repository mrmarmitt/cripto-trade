package com.marmitt.core.dto.configuration;

import com.marmitt.core.enums.StreamType;

import java.util.List;
import java.util.Map;

public record WebSocketConnectionParameters(
        List<StreamType> streamType,
        Map<String, Object> additionalParameters
) {

    public static WebSocketConnectionParameters of(List<StreamType> streams) {
        return new WebSocketConnectionParameters(streams, Map.of());
    }

    public static WebSocketConnectionParameters of(List<StreamType> streams, Map<String, Object> parameters) {
        return new WebSocketConnectionParameters(streams, parameters);
    }

    public List<String> getSymbols() {
        Object symbolsParam = additionalParameters.get("symbols");
        if (symbolsParam == null || symbolsParam.toString().trim().isEmpty()) {
            return List.of();
        }
        return List.of(symbolsParam.toString().split(","));
    }

    public boolean isMultiSymbol() {
        Object symbolsParam = additionalParameters.get("symbols");
        if (symbolsParam == null || symbolsParam.toString().trim().isEmpty()) {
            return false;
        }
        return symbolsParam.toString().contains(",");
    }

    public Object getParameterValue(String key) {
        return additionalParameters.get(key);
    }

}