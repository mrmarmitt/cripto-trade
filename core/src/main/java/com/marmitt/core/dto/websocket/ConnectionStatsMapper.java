package com.marmitt.core.dto.websocket;

import com.marmitt.core.domain.ConnectionStats;
import com.marmitt.core.enums.ReliabilityRank;

import java.time.Duration;

/**
 * Mapper para converter ConnectionStats (domain) para WebSocketStatsResponse (DTO).
 * 
 * Inclui todas as métricas avançadas de confiabilidade e performance.
 */
public class ConnectionStatsMapper {
    
    /**
     * Converte ConnectionStats do domínio para DTO de resposta com métricas avançadas.
     * 
     * @param stats estatísticas do domínio
     * @param exchangeName nome da exchange para contexto
     * @return DTO para camada de apresentação
     */
    public static WebSocketStatsResponse toResponse(ConnectionStats stats, String exchangeName) {
        if (stats == null) {
            return createEmptyResponse(exchangeName != null ? exchangeName : "UNKNOWN");
        }
        
        // Usa baseline padrão de 60 mensagens por minuto para cálculo de confiabilidade
        double reliabilityScore = stats.getReliabilityScore(60);
        ReliabilityRank reliabilityRank = ReliabilityRank.fromScore(reliabilityScore);
        
        return new WebSocketStatsResponse(
            // Métricas básicas
            stats.getTotalConnections(),
            stats.getTotalReconnections(),
            stats.getTotalMessagesReceived(),
            stats.getTotalErrors(),
            stats.getLastConnectedAt(),
            stats.getLastMessageAt(),
            exchangeName != null ? exchangeName : "UNKNOWN",
            
            // Métricas de estado
            stats.hasRecentActivity(),
            stats.hasConnections(),
            stats.hasErrors(),
            stats.getErrorRate(),
            stats.getReconnectionRate(),
            
            // Métricas de constância e frequência
            stats.getMessagesPerMinute(),
            stats.getAverageMessageInterval(),
            stats.getLongestSilencePeriod(),
            stats.getLastSilenceStart(),
            
            // Métricas de confiabilidade
            reliabilityScore,
            reliabilityRank,
            stats.getUptimePercentage(),
            stats.getTotalUptime(),
            
            // Métricas de tendência e estabilidade
            stats.getStabilityTrend(),
            stats.isCurrentlyStable(),
            determineRelativePerformance(reliabilityScore),
            
            // Métricas de latência
            stats.getAverageResponseTime(),
            
            // Métricas de qualidade
            stats.getDataQualityScore(),
            calculateExpectedVsActual(stats, 60) // Usa baseline padrão
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
            // Métricas básicas
            0L, 0L, 0L, 0L, null, null, exchangeName,
            // Métricas de estado
            false, false, false, 0.0, 0.0,
            // Métricas de constância
            0.0, Duration.ZERO, Duration.ZERO, null,
            // Métricas de confiabilidade
            0.0, ReliabilityRank.F, 0.0, Duration.ZERO,
            // Métricas de tendência
            "NO_DATA", false, "NO_DATA",
            // Métricas de latência
            Duration.ZERO,
            // Métricas de qualidade
            100.0, 0L
        );
    }
    
    /**
     * Determina performance relativa baseada no reliability score.
     */
    private static String determineRelativePerformance(double reliabilityScore) {
        if (reliabilityScore >= 80.0) return "ABOVE_AVERAGE";
        if (reliabilityScore >= 60.0) return "AVERAGE";
        return "BELOW_AVERAGE";
    }
    
    /**
     * Calcula diferença entre mensagens esperadas vs recebidas.
     * 
     * @param stats estatísticas de conexão
     * @param expectedMessagesPerMinute baseline esperado de mensagens por minuto
     */
    private static long calculateExpectedVsActual(ConnectionStats stats, long expectedMessagesPerMinute) {
        if (stats.getTotalUptime().isZero()) {
            return 0L;
        }
        
        long uptimeMinutes = stats.getTotalUptime().toMinutes();
        long expectedMessages = expectedMessagesPerMinute * uptimeMinutes;
        
        return expectedMessages - stats.getTotalMessagesReceived();
    }
}