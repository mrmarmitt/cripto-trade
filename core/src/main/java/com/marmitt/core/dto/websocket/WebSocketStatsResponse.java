package com.marmitt.core.dto.websocket;

import java.time.Instant;

/**
 * DTO para representar estatísticas de conexão WebSocket na camada de apresentação.
 * 
 * Encapsula métricas agregadas de conexão, mensagens e erros
 * para uma exchange específica.
 */
public record WebSocketStatsResponse(
        long totalConnections,
        long totalReconnections, 
        long totalMessagesReceived,
        long totalErrors,
        Instant lastConnectedAt,
        Instant lastMessageAt,
        String exchangeName,
        boolean hasRecentActivity,
        boolean hasConnections,
        boolean hasErrors,
        double errorRate,
        double reconnectionRate) {
}