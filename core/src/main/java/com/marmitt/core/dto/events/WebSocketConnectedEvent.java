package com.marmitt.core.dto.events;

import com.marmitt.core.enums.StreamChannel;

import java.time.Instant;
import java.util.UUID;

public record WebSocketConnectedEvent(
        String exchange,
        String message,
        UUID connectionId,
        Instant timestamp,
        boolean wasReconnection,
        StreamChannel channel
) {

    public static WebSocketConnectedEvent of(String exchange, String message, UUID connectionId, StreamChannel channel) {
        return new WebSocketConnectedEvent(exchange, message, connectionId, Instant.now(), false, channel);
    }

    public static WebSocketConnectedEvent reconnection(String exchange, String message, UUID connectionId, StreamChannel channel) {
        return new WebSocketConnectedEvent(exchange, message, connectionId, Instant.now(), true, channel);
    }
}
