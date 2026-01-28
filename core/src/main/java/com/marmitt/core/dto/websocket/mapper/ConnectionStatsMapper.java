package com.marmitt.core.dto.websocket.mapper;

import com.marmitt.core.dto.connection.ConnectionStatsDto;
import com.marmitt.core.dto.websocket.response.WebSocketStatsResponse;
import com.marmitt.core.enums.ReliabilityRank;

import java.time.Duration;

public class ConnectionStatsMapper {

    public static WebSocketStatsResponse toResponse(ConnectionStatsDto stats, String exchangeName) {
        if (stats == null) {
            return createEmptyResponse(exchangeName != null ? exchangeName : "UNKNOWN");
        }

        // Usa baseline padrão de 60 mensagens por minuto para cálculo de confiabilidade
        double reliabilityScore = stats.getReliabilityScore(60);
        ReliabilityRank reliabilityRank = ReliabilityRank.fromScore(reliabilityScore);

        return new WebSocketStatsResponse(
                // Métricas básicas
                stats.getTotalConnections(),
                stats.getTotalDisconnections(),
                stats.getTotalReconnections(),
                stats.getTotalMessagesReceived(),
                stats.getTotalErrors(),
                stats.getLastConnectedAt(),
                stats.getLastDisconnectedAt(),
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

    public static WebSocketStatsResponse createEmptyResponse(String exchangeName) {
        return new WebSocketStatsResponse(
                0L,
                0L,
                0L,
                0L,
                0L,
                null,
                null,
                null,
                exchangeName,
                false,
                false,
                false,
                0.0,
                0.0,
                0.0,
                Duration.ZERO,
                Duration.ZERO,
                null,
                0.0,
                ReliabilityRank.F,
                0.0,
                Duration.ZERO,
                "NO_DATA",
                false,
                "NO_DATA",
                Duration.ZERO,
                100.0,
                0L);
    }

    private static String determineRelativePerformance(double reliabilityScore) {
        if (reliabilityScore >= 80.0) return "ABOVE_AVERAGE";
        if (reliabilityScore >= 60.0) return "AVERAGE";
        return "BELOW_AVERAGE";
    }

    private static long calculateExpectedVsActual(ConnectionStatsDto stats, long expectedMessagesPerMinute) {
        if (stats.getTotalUptime().isZero()) {
            return 0L;
        }

        long uptimeMinutes = stats.getTotalUptime().toMinutes();
        long expectedMessages = expectedMessagesPerMinute * uptimeMinutes;

        return stats.getTotalMessagesReceived() - expectedMessages;
    }
}