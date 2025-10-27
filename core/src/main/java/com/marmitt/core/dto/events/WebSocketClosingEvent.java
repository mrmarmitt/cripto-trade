package com.marmitt.core.dto.events;

import java.time.Instant;
import java.util.UUID;

public record WebSocketClosingEvent(
        String exchange,
        int code,
        String reason,
        UUID connectionId,
        Instant timestamp
) {
    
    public static WebSocketClosingEvent of(String exchange, int code, String reason) {
        return new WebSocketClosingEvent(exchange, code, reason, null, Instant.now());
    }
    
    public static WebSocketClosingEvent of(String exchange, int code, String reason, UUID connectionId) {
        return new WebSocketClosingEvent(exchange, code, reason, connectionId, Instant.now());
    }
}