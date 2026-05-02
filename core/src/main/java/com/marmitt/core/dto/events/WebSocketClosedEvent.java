package com.marmitt.core.dto.events;

import com.marmitt.core.enums.StreamChannel;

import java.time.Instant;
import java.util.UUID;

public record WebSocketClosedEvent(
        String exchange,
        int code,
        String reason,
        UUID connectionId,
        Instant timestamp,
        boolean wasExpected,
        StreamChannel channel
) {

    public static WebSocketClosedEvent of(String exchange, int code, String reason, UUID connectionId, StreamChannel channel) {
        return new WebSocketClosedEvent(exchange, code, reason, connectionId, Instant.now(), isExpectedCloseCode(code), channel);
    }

    public static WebSocketClosedEvent unexpected(String exchange, int code, String reason, UUID connectionId, StreamChannel channel) {
        return new WebSocketClosedEvent(exchange, code, reason, connectionId, Instant.now(), false, channel);
    }

    private static boolean isExpectedCloseCode(int code) {
        return code == 1000 || code == 1001 || code == 1005;
    }
}
