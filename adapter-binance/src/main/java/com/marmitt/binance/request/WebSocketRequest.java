package com.marmitt.binance.request;

import java.util.Map;

public record WebSocketRequest(
        String id,
        String method,
        Map<String, Object> params
) {
}