package com.marmitt.core.dto.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Event published when a WebSocket connection is successfully established.
 */
public record WebSocketConnectedEvent(
        String exchange,
        String message,
        UUID connectionId,
        Instant timestamp,
        boolean wasReconnection
) {
    
    public static WebSocketConnectedEvent of(String exchange, String message, UUID connectionId) {
        return new WebSocketConnectedEvent(exchange, message, connectionId, Instant.now(), false);
    }
    
    public static WebSocketConnectedEvent reconnection(String exchange, String message, UUID connectionId) {
        return new WebSocketConnectedEvent(exchange, message, connectionId, Instant.now(), true);
    }
}