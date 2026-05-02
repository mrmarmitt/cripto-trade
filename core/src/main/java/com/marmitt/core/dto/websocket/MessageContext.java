package com.marmitt.core.dto.websocket;

import com.marmitt.core.enums.StreamChannel;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public record MessageContext(
    UUID correlationId,
    String exchangeName,
    Instant receivedAt,
    UUID connectionId,
    Map<String, String> headers,
    StreamChannel streamChannel
) {

    public static MessageContext create(String exchangeName, UUID connectionId) {
        return new MessageContext(
            UUID.randomUUID(),
            exchangeName,
            Instant.now(),
            connectionId,
            new HashMap<>(),
            StreamChannel.MARKET
        );
    }

    public static MessageContext createUserData(String exchangeName, UUID connectionId) {
        return new MessageContext(
            UUID.randomUUID(),
            exchangeName,
            Instant.now(),
            connectionId,
            new HashMap<>(),
            StreamChannel.USER_DATA
        );
    }

    public MessageContext withHeader(String key, String value) {
        Map<String, String> newHeaders = new HashMap<>(this.headers);
        newHeaders.put(key, value);
        return new MessageContext(correlationId, exchangeName, receivedAt, connectionId, newHeaders, streamChannel);
    }
}
