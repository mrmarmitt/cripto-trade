package com.marmitt.core.dto.connection;

import lombok.Getter;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * DTO representando estatísticas de conexão WebSocket.
 * 
 * Contém métricas agregadas incluindo confiabilidade, constância e tendências.
 */
public class ConnectionStatsDto {

    // Getters
    // Métricas básicas
    @Getter
    private long totalConnections;
    @Getter
    private long totalDisconnections;
    @Getter
    private long totalReconnections;
    @Getter
    private long totalMessagesReceived;
    @Getter
    private long totalErrors;
    @Getter
    private Instant lastConnectedAt;
    @Getter
    private Instant lastDisconnectedAt;
    @Getter
    private Instant lastMessageAt;
    
    // Métricas de timing
    private Instant connectionStartTime;
    private Instant disconnectionStartTime;
    private final List<Instant> messageTimestamps;
    @Getter
    private Instant lastSilenceStart;
    
    // Métricas de performance
    private final List<Long> messageCountHistory;
    private final List<Duration> responseTimes;
    
    // Métricas de qualidade
    private long malformedMessages;
    private long duplicateMessages;
    
    // Auxiliar para tracking por minuto
    private transient Map<Long, Long> messageCountPerMinute;

    public ConnectionStatsDto() {
        this.totalConnections = 0;
        this.totalDisconnections = 0;
        this.totalReconnections = 0;
        this.totalMessagesReceived = 0;
        this.totalErrors = 0;
        this.lastConnectedAt = null;
        this.lastDisconnectedAt = null;
        this.lastMessageAt = null;
        this.connectionStartTime = null;
        this.disconnectionStartTime = null;
        this.messageTimestamps = new ArrayList<>();
        this.lastSilenceStart = null;
        this.messageCountHistory = new ArrayList<>();
        this.responseTimes = new ArrayList<>();
        this.malformedMessages = 0;
        this.duplicateMessages = 0;
        this.messageCountPerMinute = new ConcurrentHashMap<>();
    }

    public static ConnectionStatsDto empty() {
        return new ConnectionStatsDto();
    }

    public List<Instant> getMessageTimestamps() { return new ArrayList<>(messageTimestamps); }
    public List<Long> getMessageCountHistory() { return new ArrayList<>(messageCountHistory); }
    public List<Duration> getResponseTimes() { return new ArrayList<>(responseTimes); }


    // Methods for updating stats
    public void recordConnection() {
        this.totalConnections++;
        Instant now = Instant.now();
        this.lastConnectedAt = now;
        
        if (this.connectionStartTime == null) {
            this.connectionStartTime = now;
        }
    }

    public void recordDisconnection() {
        this.totalDisconnections++;
        Instant now = Instant.now();
        this.lastDisconnectedAt = now;

        if (this.disconnectionStartTime == null) {
            this.disconnectionStartTime = now;
        }
    }

    public void recordReconnection() {
        this.totalReconnections++;
        Instant now = Instant.now();
        this.lastConnectedAt = now;
        
        if (this.connectionStartTime == null) {
            this.connectionStartTime = now;
        }
    }

    public void recordMessage() {
        recordMessage(Instant.now());
    }

    public void recordMessage(Instant timestamp) {
        this.totalMessagesReceived++;
        this.lastMessageAt = timestamp;
        
        messageTimestamps.add(timestamp);
        if (messageTimestamps.size() > 100) {
            messageTimestamps.removeFirst();
        }
        
        updateMessageCountHistory(timestamp);
        
        if (lastSilenceStart != null) {
            lastSilenceStart = null;
        }
    }

    public void recordError() {
        this.totalErrors++;
    }
    
    public void recordResponseTime(Duration responseTime) {
        this.responseTimes.add(responseTime);
        if (responseTimes.size() > 100) {
            responseTimes.removeFirst();
        }
    }

    public void recordMalformedMessage() {
        this.malformedMessages++;
    }

    public void recordDuplicateMessage() {
        this.duplicateMessages++;
    }

    public void markSilenceStart() {
        if (lastSilenceStart == null) {
            this.lastSilenceStart = Instant.now();
        }
    }

    public Duration getTotalUptime() {
        if (connectionStartTime == null) {
            return Duration.ZERO;
        }
        return Duration.between(connectionStartTime, Instant.now());
    }

    public double getErrorRate() {
        if (totalConnections == 0) {
            return 0.0;
        }
        return (double) totalErrors / totalConnections * 100.0;
    }

    public double getReconnectionRate() {
        if (totalConnections == 0) {
            return 0.0;
        }
        return (double) totalReconnections / totalConnections * 100.0;
    }

    public boolean hasRecentActivity() {
        return lastMessageAt != null;
    }

    public boolean hasConnections() {
        return totalConnections > 0;
    }

    public boolean hasErrors() {
        return totalErrors > 0;
    }

    private void updateMessageCountHistory(Instant timestamp) {
        long currentMinute = timestamp.truncatedTo(ChronoUnit.MINUTES).getEpochSecond() / 60;
        
        messageCountPerMinute.merge(currentMinute, 1L, Long::sum);
        
        long tenMinutesAgo = currentMinute - 10;
        messageCountPerMinute.entrySet().removeIf(entry -> entry.getKey() < tenMinutesAgo);
        
        updateMessageCountHistoryList();
    }

    private void updateMessageCountHistoryList() {
        messageCountHistory.clear();
        messageCountPerMinute.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> messageCountHistory.add(entry.getValue()));
    }

    // Métodos de análise e métricas calculadas
    
    public double getReliabilityScore(long expectedMessagesPerMinute) {
        if (getTotalUptime().isZero()) {
            return 0.0;
        }
        
        double uptimeMinutes = getTotalUptime().toMinutes();
        double expectedMessages = expectedMessagesPerMinute * uptimeMinutes;
        
        if (expectedMessages == 0) {
            return 0.0;
        }
        
        double actualRatio = totalMessagesReceived / expectedMessages;
        double errorPenalty = Math.max(0, 100.0 - getErrorRate());
        
        return Math.min(100.0, actualRatio * errorPenalty);
    }
    
    public double getMessagesPerMinute() {
        Duration uptime = getTotalUptime();
        if (uptime.isZero()) {
            return 0.0;
        }
        
        double minutes = uptime.toMinutes();
        return minutes > 0 ? totalMessagesReceived / minutes : 0.0;
    }
    
    public Duration getAverageMessageInterval() {
        if (messageTimestamps.size() < 2) {
            return Duration.ZERO;
        }
        
        long totalMillis = 0;
        for (int i = 1; i < messageTimestamps.size(); i++) {
            totalMillis += Duration.between(messageTimestamps.get(i-1), messageTimestamps.get(i)).toMillis();
        }
        
        return Duration.ofMillis(totalMillis / (messageTimestamps.size() - 1));
    }
    
    public Duration getLongestSilencePeriod() {
        if (messageTimestamps.size() < 2) {
            return Duration.ZERO;
        }
        
        Duration longest = Duration.ZERO;
        for (int i = 1; i < messageTimestamps.size(); i++) {
            Duration gap = Duration.between(messageTimestamps.get(i-1), messageTimestamps.get(i));
            if (gap.compareTo(longest) > 0) {
                longest = gap;
            }
        }
        
        return longest;
    }
    
    public double getUptimePercentage() {
        if (connectionStartTime == null) {
            return 0.0;
        }
        
        Duration totalTime = Duration.between(connectionStartTime, Instant.now());
        Duration uptime = getTotalUptime();
        
        if (totalTime.isZero()) {
            return 0.0;
        }
        
        return (uptime.toMillis() / (double) totalTime.toMillis()) * 100.0;
    }
    
    public String getStabilityTrend() {
        if (messageCountHistory.size() < 3) {
            return "INSUFFICIENT_DATA";
        }
        
        List<Long> recent = messageCountHistory.subList(Math.max(0, messageCountHistory.size() - 3), messageCountHistory.size());
        
        boolean increasing = recent.get(1) > recent.get(0) && recent.get(2) > recent.get(1);
        boolean decreasing = recent.get(1) < recent.get(0) && recent.get(2) < recent.get(1);
        
        if (increasing) return "IMPROVING";
        if (decreasing) return "DEGRADING";
        return "STABLE";
    }
    
    public boolean isCurrentlyStable() {
        if (messageCountHistory.size() < 2) {
            return false;
        }
        
        // Considera estável se as últimas medições não variam mais que 20%
        List<Long> recent = messageCountHistory.subList(Math.max(0, messageCountHistory.size() - 2), messageCountHistory.size());
        
        if (recent.size() < 2) return false;
        
        long avg = recent.stream().mapToLong(Long::longValue).sum() / recent.size();
        if (avg == 0) return false;
        
        return recent.stream().allMatch(count -> 
            Math.abs(count - avg) / (double) avg <= 0.2
        );
    }
    
    public Duration getAverageResponseTime() {
        if (responseTimes.isEmpty()) {
            return Duration.ZERO;
        }
        
        long totalMillis = responseTimes.stream()
            .mapToLong(Duration::toMillis)
            .sum();
            
        return Duration.ofMillis(totalMillis / responseTimes.size());
    }
    
    public double getDataQualityScore() {
        if (totalMessagesReceived == 0) {
            return 100.0;
        }
        
        double malformedRate = (malformedMessages / (double) totalMessagesReceived) * 100.0;
        double duplicateRate = (duplicateMessages / (double) totalMessagesReceived) * 100.0;
        
        return Math.max(0.0, 100.0 - malformedRate - duplicateRate);
    }
    
    public void resetCounters() {
        // Reset implementation if needed
    }
}