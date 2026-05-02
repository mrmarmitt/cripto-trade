package com.marmitt.core.dto.events;

import com.marmitt.core.enums.StreamChannel;

import java.time.Instant;
import java.util.UUID;

public record WebSocketDisconnectedEvent(
        String exchange,
        String reason,
        UUID connectionId,
        Instant timestamp,
        boolean wasManual,
        StreamChannel channel
) {

    public static WebSocketDisconnectedEvent manual(String exchange, String reason, UUID connectionId, StreamChannel channel) {
        return new WebSocketDisconnectedEvent(exchange, reason, connectionId, Instant.now(), true, channel);
    }

    public static WebSocketDisconnectedEvent automatic(String exchange, String reason, UUID connectionId, StreamChannel channel) {
        return new WebSocketDisconnectedEvent(exchange, reason, connectionId, Instant.now(), false, channel);
    }
}
