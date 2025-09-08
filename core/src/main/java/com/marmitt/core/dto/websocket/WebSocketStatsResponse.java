package com.marmitt.core.dto.websocket;

import com.marmitt.core.domain.ReliabilityRank;

import java.time.Duration;
import java.time.Instant;

/**
 * DTO para representar estatísticas avançadas de conexão WebSocket na camada de apresentação.
 * 
 * Inclui métricas de confiabilidade, constância e tendências para análise operacional.
 */
public record WebSocketStatsResponse(
        // Métricas básicas
        long totalConnections,
        long totalReconnections, 
        long totalMessagesReceived,
        long totalErrors,
        Instant lastConnectedAt,
        Instant lastMessageAt,
        String exchangeName,
        
        // Métricas de estado
        boolean hasRecentActivity,
        boolean hasConnections,
        boolean hasErrors,
        double errorRate,
        double reconnectionRate,
        
        // Métricas de constância e frequência
        double messagesPerMinute,
        Duration averageMessageInterval,
        Duration longestSilencePeriod,
        Instant lastSilenceStart,
        
        // Métricas de confiabilidade
        double reliabilityScore,        // 0-100
        ReliabilityRank reliabilityRank,
        double uptimePercentage,
        Duration connectionUptime,
        
        // Métricas de tendência e estabilidade
        String stabilityTrend,          // "STABLE", "IMPROVING", "DEGRADING"
        boolean isCurrentlyStable,
        String relativePerformance,     // "ABOVE_AVERAGE", "AVERAGE", "BELOW_AVERAGE"
        
        // Métricas de latência
        Duration averageResponseTime,
        
        // Métricas de qualidade
        double dataQualityScore,        // 0-100
        long expectedVsActualMessages   // Diferença entre esperado vs recebido
) {
}