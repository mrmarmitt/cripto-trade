package com.marmitt.core.dto.websocket;

import com.marmitt.core.domain.ConnectionStats;

/**
 * Mapper para converter ConnectionStats (domain) para WebSocketStatsResponse (DTO).
 * 
 * Segue o mesmo padrão do ConnectionResultMapper para manter consistência.
 */
public class ConnectionStatsMapper {
    
    /**
     * Converte ConnectionStats do domínio para DTO de resposta.
     * 
     * @param stats estatísticas do domínio
     * @param exchangeName nome da exchange para contexto
     * @return DTO para camada de apresentação
     */
    public static WebSocketStatsResponse toResponse(ConnectionStats stats, String exchangeName) {
        if (stats == null) {
            return createEmptyResponse(exchangeName != null ? exchangeName : "UNKNOWN");
        }
        
        return new WebSocketStatsResponse(
            stats.totalConnections(),
            stats.totalReconnections(),
            stats.totalMessagesReceived(),
            stats.totalErrors(),
            stats.lastConnectedAt(),
            stats.lastMessageAt(),
            exchangeName != null ? exchangeName : "UNKNOWN",
            stats.hasRecentActivity(),
            stats.hasConnections(),
            stats.hasErrors(),
            stats.getErrorRate(),
            stats.getReconnectionRate()
        );
    }
    
    /**
     * Cria uma resposta vazia para casos onde não há estatísticas.
     * 
     * @param exchangeName nome da exchange
     * @return DTO com estatísticas zeradas
     */
    public static WebSocketStatsResponse createEmptyResponse(String exchangeName) {
        return new WebSocketStatsResponse(
            0L, 0L, 0L, 0L,
            null, null,
            exchangeName,
            false, false, false,
            0.0, 0.0
        );
    }
    
}