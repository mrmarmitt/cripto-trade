package com.marmitt.core.dto.events;

import com.marmitt.core.enums.StreamChannel;

import java.time.Instant;
import java.util.UUID;

public record WebSocketFailedEvent(
        String exchange,
        String reason,
        UUID connectionId,
        Throwable cause,
        Instant timestamp,
        boolean isCritical,
        int attemptCount,
        StreamChannel channel
) {

    public static WebSocketFailedEvent of(String exchange, String reason, UUID connectionId, Throwable cause, StreamChannel channel) {
        return new WebSocketFailedEvent(exchange, reason, connectionId, cause, Instant.now(), false, 1, channel);
    }

    public static WebSocketFailedEvent withAttempts(String exchange, String reason, UUID connectionId, Throwable cause, int attemptCount, StreamChannel channel) {
        return new WebSocketFailedEvent(exchange, reason, connectionId, cause, Instant.now(), attemptCount >= 5, attemptCount, channel);
    }
}
