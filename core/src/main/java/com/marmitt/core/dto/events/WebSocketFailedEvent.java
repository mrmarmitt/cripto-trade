package com.marmitt.core.dto.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Event published when a WebSocket connection fails to establish or encounters an error.
 */
public record WebSocketFailedEvent(
        String exchange,
        String reason,
        UUID connectionId,
        Throwable cause,
        Instant timestamp,
        boolean isCritical,
        int attemptCount
) {
    
    public static WebSocketFailedEvent of(String exchange, String reason, Throwable cause) {
        return new WebSocketFailedEvent(exchange, reason, null, cause, Instant.now(), false, 1);
    }
    
    public static WebSocketFailedEvent of(String exchange, String reason, UUID connectionId, Throwable cause) {
        return new WebSocketFailedEvent(exchange, reason, connectionId, cause, Instant.now(), false, 1);
    }
    
    public static WebSocketFailedEvent critical(String exchange, String reason, UUID connectionId, Throwable cause) {
        return new WebSocketFailedEvent(exchange, reason, connectionId, cause, Instant.now(), true, 1);
    }
    
    public static WebSocketFailedEvent withAttempts(String exchange, String reason, UUID connectionId, Throwable cause, int attemptCount) {
        boolean critical = attemptCount >= 5; // Consider critical after 5 attempts
        return new WebSocketFailedEvent(exchange, reason, connectionId, cause, Instant.now(), critical, attemptCount);
    }
}