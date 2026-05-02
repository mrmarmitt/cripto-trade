package com.marmitt.core.dto.events;

import com.marmitt.core.enums.StreamChannel;

import java.time.Instant;
import java.util.UUID;

public record WebSocketClosingEvent(
        String exchange,
        int code,
        String reason,
        UUID connectionId,
        Instant timestamp,
        StreamChannel channel
) {

    public static WebSocketClosingEvent of(String exchange, int code, String reason, UUID connectionId, StreamChannel channel) {
        return new WebSocketClosingEvent(exchange, code, reason, connectionId, Instant.now(), channel);
    }
}
