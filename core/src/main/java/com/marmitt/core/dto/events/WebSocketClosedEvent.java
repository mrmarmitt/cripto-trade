package com.marmitt.core.dto.events;

import java.time.Instant;
import java.util.UUID;

public record WebSocketClosedEvent(
        String exchange,
        int code,
        String reason,
        UUID connectionId,
        Instant timestamp,
        boolean wasExpected
) {
    
    public static WebSocketClosedEvent of(String exchange, int code, String reason) {
        boolean expected = isExpectedCloseCode(code);
        return new WebSocketClosedEvent(exchange, code, reason, null, Instant.now(), expected);
    }
    
    public static WebSocketClosedEvent of(String exchange, int code, String reason, UUID connectionId) {
        boolean expected = isExpectedCloseCode(code);
        return new WebSocketClosedEvent(exchange, code, reason, connectionId, Instant.now(), expected);
    }
    
    public static WebSocketClosedEvent unexpected(String exchange, int code, String reason, UUID connectionId) {
        return new WebSocketClosedEvent(exchange, code, reason, connectionId, Instant.now(), false);
    }
    
    private static boolean isExpectedCloseCode(int code) {
        return code == 1000 || // Normal closure
               code == 1001 || // Going away
               code == 1005;   // No status received
    }
}