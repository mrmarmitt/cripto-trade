package com.marmitt.binance.response;

import java.util.List;
import java.util.Map;

public record WebSocketResponse(
        String id,
        Integer status,
        Map<String, Object> result,
        List<RateLimit> rateLimits
) {
    public record RateLimit(
            String rateLimitType,
            String interval,
            Integer intervalNum,
            Integer limit,
            Integer count
    ) {}
}