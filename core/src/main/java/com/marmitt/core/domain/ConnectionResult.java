package com.marmitt.core.domain;

import com.marmitt.core.enums.ConnectionStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
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

    public ConnectionResult connecting() {
        return new ConnectionResult(
                ConnectionStatus.CONNECTING,
                "Establishing connection...",
                Instant.now(),
                UUID.randomUUID(),
                this.metadata
        );
    }

    public ConnectionResult connected() {
        Map<String, Object> newMetadata = new HashMap<>(this.metadata);
        newMetadata.put("connectionAt", Instant.now());

        return new ConnectionResult(
                ConnectionStatus.CONNECTED,
                "Connection established successfully",
                Instant.now(),
                this.connectionId,
                newMetadata
        );
    }

    public static ConnectionResult failure(String reason) {
        return new ConnectionResult(
                ConnectionStatus.ERROR,
                reason,
                Instant.now(),
                UUID.randomUUID(),
                Map.of("error", reason)
        );
    }

    public ConnectionResult failed(String reason, Throwable cause) {
        Map<String, Object> newMetadata = new HashMap<>(this.metadata);
        newMetadata.put("error", cause.getMessage());
        newMetadata.put("errorType", cause.getClass().getSimpleName());
        
        return new ConnectionResult(
                ConnectionStatus.ERROR,
                reason,
                Instant.now(),
                this.connectionId,
                newMetadata
        );
    }

    public ConnectionResult disconnected(String reason) {
        Map<String, Object> newMetadata = new HashMap<>(this.metadata);
        newMetadata.put("disconnectionAt", Instant.now());
        newMetadata.put("disconnectReason", reason);
        
        return new ConnectionResult(
                ConnectionStatus.DISCONNECTED,
                reason,
                Instant.now(),
                this.connectionId,
                newMetadata
        );
    }

    public ConnectionResult closing(int code, String reason) {
        Map<String, Object> newMetadata = new HashMap<>(this.metadata);
        newMetadata.put("closeCode", code);
        newMetadata.put("closeReason", reason);
        
        return new ConnectionResult(
                ConnectionStatus.CLOSING,
                reason,
                Instant.now(),
                this.connectionId,
                newMetadata
        );
    }

    public ConnectionResult closed(int code, String reason) {
        Map<String, Object> newMetadata = new HashMap<>(this.metadata);
        newMetadata.put("closedAt", Instant.now());
        newMetadata.put("closeCode", code);
        newMetadata.put("closeReason", reason);
        
        return new ConnectionResult(
                ConnectionStatus.CLOSED,
                reason,
                Instant.now(),
                this.connectionId,
                newMetadata
        );
    }

    public ConnectionResult withMetadata(String key, Object value) {
        Map<String, Object> newMetadata = new HashMap<>(this.metadata);
        newMetadata.put(key, value);
        return new ConnectionResult(
                this.status,
                this.message,
                this.timestamp,
                this.connectionId,
                newMetadata);
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
