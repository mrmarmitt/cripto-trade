package com.marmitt.core.domain;

import com.marmitt.core.enums.ConnectionStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record ConnectionResult(
        ConnectionStatus status,
        String message,
        Instant timestamp,
        UUID connectionId,
        Map<String, Object> metadata) {

    public static ConnectionResult idle() {
        return new ConnectionResult(
                ConnectionStatus.IDLE,
                "WebSocket adapter initialized, ready to connect",
                Instant.now(),
                null,
                Map.of("initialized", true)
        );
    }

    public static ConnectionResult connecting() {
        return new ConnectionResult(
                ConnectionStatus.CONNECTING,
                "Establishing connection...",
                Instant.now(),
                UUID.randomUUID(),
                Map.of()
        );
    }

    public static ConnectionResult connected(String message, UUID connectionId) {
        return new ConnectionResult(
                ConnectionStatus.CONNECTED,
                message,
                Instant.now(),
                connectionId,
                Map.of("connectionAt", Instant.now())
        );
    }

    public static ConnectionResult disconnecting(String reason, UUID connectionId) {
        return new ConnectionResult(
                ConnectionStatus.DISCONNECTING,
                reason,
                Instant.now(),
                connectionId,
                Map.of()
        );
    }

    public static ConnectionResult disconnected(String reason, UUID connectionId) {
        Map<String, Object> metadata = Map.of(
                "disconnectionAt", Instant.now(),
                "disconnectReason", reason
        );

        return new ConnectionResult(
                ConnectionStatus.DISCONNECTED,
                reason,
                Instant.now(),
                connectionId,
                metadata
        );
    }

    public static ConnectionResult failure(String reason) {
        return new ConnectionResult(
                ConnectionStatus.ERROR,
                reason,
                Instant.now(),
                null,
                Map.of("error", reason)
        );
    }

    public static ConnectionResult failure(String reason, Throwable cause) {
        Map<String, Object> metadata = Map.of(
                "error", reason,
                "errorMessage", cause.getMessage(),
                "errorType", cause.getClass().getSimpleName()
        );
        
        return new ConnectionResult(
                ConnectionStatus.ERROR,
                reason,
                Instant.now(),
                null,
                metadata
        );
    }

    public static ConnectionResult failure(String reason, UUID connectionId, Throwable cause) {
        Map<String, Object> metadata = Map.of(
                "error", reason,
                "errorMessage", cause.getMessage(),
                "errorType", cause.getClass().getSimpleName()
        );
        
        return new ConnectionResult(
                ConnectionStatus.ERROR,
                reason,
                Instant.now(),
                connectionId,
                metadata
        );
    }

    public static ConnectionResult closing(int code, String reason) {
        Map<String, Object> metadata = Map.of(
                "closeCode", code,
                "closeReason", reason
        );
        
        return new ConnectionResult(
                ConnectionStatus.CLOSING,
                reason,
                Instant.now(),
                null,
                metadata
        );
    }

    public static ConnectionResult closing(int code, String reason, UUID connectionId) {
        Map<String, Object> metadata = Map.of(
                "closeCode", code,
                "closeReason", reason
        );
        
        return new ConnectionResult(
                ConnectionStatus.CLOSING,
                reason,
                Instant.now(),
                connectionId,
                metadata
        );
    }

    public static ConnectionResult closed(int code, String reason) {
        Map<String, Object> metadata = Map.of(
                "closedAt", Instant.now(),
                "closeCode", code,
                "closeReason", reason
        );
        
        return new ConnectionResult(
                ConnectionStatus.CLOSED,
                reason,
                Instant.now(),
                null,
                metadata
        );
    }

    public static ConnectionResult closed(int code, String reason, UUID connectionId) {
        Map<String, Object> metadata = Map.of(
                "closedAt", Instant.now(),
                "closeCode", code,
                "closeReason", reason
        );
        
        return new ConnectionResult(
                ConnectionStatus.CLOSED,
                reason,
                Instant.now(),
                connectionId,
                metadata
        );
    }

    public static ConnectionResult withMetadata(ConnectionStatus status, String message, Map<String, Object> metadata) {
        return new ConnectionResult(
                status,
                message,
                Instant.now(),
                null,
                Map.copyOf(metadata)
        );
    }

    public static ConnectionResult withMetadata(ConnectionStatus status, String message, UUID connectionId, Map<String, Object> metadata) {
        return new ConnectionResult(
                status,
                message,
                Instant.now(),
                connectionId,
                Map.copyOf(metadata)
        );
    }

    public boolean isDisconnecting() {
        return status == ConnectionStatus.DISCONNECTING;
    }
    public boolean isConnected() {
        return status == ConnectionStatus.CONNECTED;
    }

    public boolean isFailed() {
        return status == ConnectionStatus.ERROR;
    }

    public boolean isInProgress() {
        return status == ConnectionStatus.CONNECTING || status == ConnectionStatus.RECONNECTING;
    }

    public boolean isSuccess() {
        return status == ConnectionStatus.CONNECTED || status == ConnectionStatus.IDLE;
    }

    public Duration getConnectionDuration() {
        Instant startTime = (Instant) metadata.get("connectionAt");
        if (startTime == null) {
            return Duration.ZERO;
        }
        return Duration.between(startTime, Instant.now());
    }


}
