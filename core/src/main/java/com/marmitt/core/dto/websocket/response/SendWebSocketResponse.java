package com.marmitt.core.dto.websocket.response;

import java.time.Instant;

public record SendWebSocketResponse (
        Instant timestamp,
        String exchangeName,
        String errorMessage){

    public static SendWebSocketResponse failure(String exchangeName, String errorMessage) {
        return new SendWebSocketResponse(Instant.now(), exchangeName, errorMessage);
    }

    public static SendWebSocketResponse success(String exchangeName) {
        return new SendWebSocketResponse(Instant.now(), exchangeName, null);
    }
}
