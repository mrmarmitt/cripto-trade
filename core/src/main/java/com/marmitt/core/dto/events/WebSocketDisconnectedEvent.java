package com.marmitt.core.dto.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Event published when a WebSocket connection is manually disconnected.
 */
public record WebSocketDisconnectedEvent(
        String exchange,
        String reason,
        UUID connectionId,
        Instant timestamp,
        boolean wasManual
) {
    
    public static WebSocketDisconnectedEvent manual(String exchange, String reason) {
        return new WebSocketDisconnectedEvent(exchange, reason, null, Instant.now(), true);
    }
    
    public static WebSocketDisconnectedEvent manual(String exchange, String reason, UUID connectionId) {
        return new WebSocketDisconnectedEvent(exchange, reason, connectionId, Instant.now(), true);
    }
    
    public static WebSocketDisconnectedEvent automatic(String exchange, String reason, UUID connectionId) {
        return new WebSocketDisconnectedEvent(exchange, reason, connectionId, Instant.now(), false);
    }
}