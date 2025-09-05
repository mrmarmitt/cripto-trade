package com.marmitt.core.dto.websocket;

import com.marmitt.core.enums.ConnectionStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

public record WebSocketConnectionResponse(
        ConnectionStatus status,
        String message,
        Instant timestamp,
        String exchangeName,
        String connectionId,
        Map<String, Object> metadata,
        boolean isConnected,
        boolean isSuccess,
        boolean isInProgress,
        boolean isFailed,
        Duration connectionDuration) {
}