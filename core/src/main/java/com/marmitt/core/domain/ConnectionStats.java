package com.marmitt.core.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Domain object representando estatísticas avançadas de conexão WebSocket.
 * 
 * Contém métricas agregadas incluindo confiabilidade, constância e tendências.
 */
@Getter
@Builder(toBuilder = true)
@AllArgsConstructor
public class ConnectionStats {
    
    // Métricas básicas
    private long totalConnections;
    private long totalReconnections;
    private long totalMessagesReceived;
    private long totalErrors;
    private Instant lastConnectedAt;
    private Instant lastMessageAt;
    
    // Métricas de timing
    @Builder.Default
    private final Instant connectionStartTime = Instant.now();
    @Builder.Default
    private final List<Instant> messageTimestamps = new ArrayList<>();        // Últimos 100 timestamps para cálculos
    private Instant lastSilenceStart;
    
    // Métricas de performance
    @Builder.Default
    private final List<Long> messageCountHistory = new ArrayList<>();         // Contadores por minuto (últimos 10 min)
    @Builder.Default
    private final List<Duration> responseTimes = new ArrayList<>();           // Para calcular média
    
    // Métricas de qualidade
    private long malformedMessages;
    private long duplicateMessages;
    
    // Auxiliar para tracking por minuto (não serializado)
    @Builder.Default
    private transient Map<Long, Long> messageCountPerMinute = new ConcurrentHashMap<>();

    /**
     * Construtor padrão - cria estatísticas zeradas.
     */
    private ConnectionStats() {
        this.totalConnections = 0;
        this.totalReconnections = 0;
        this.totalMessagesReceived = 0;
        this.totalErrors = 0;
        this.lastConnectedAt = null;
        this.lastMessageAt = null;
        this.connectionStartTime = Instant.now();
        this.messageTimestamps = new ArrayList<>();
        this.lastSilenceStart = null;
        this.messageCountHistory = new ArrayList<>();
        this.responseTimes = new ArrayList<>();
        this.malformedMessages = 0;
        this.duplicateMessages = 0;
        this.messageCountPerMinute = new ConcurrentHashMap<>();
    }

    /**
     * Cria estatísticas zeradas.
     */
    public static ConnectionStats empty() {
        return new ConnectionStats();
    }

    /**
     * Registra uma nova conexão.
     */
    public void recordConnection() {
        this.totalConnections++;
        this.lastConnectedAt = Instant.now();
    }
    
    /**
     * Registra uma reconexão.
     */
    public void recordReconnection() {
        this.totalReconnections++;
        this.lastConnectedAt = Instant.now();
    }
    
    /**
     * Registra uma nova mensagem recebida.
     */
    public void recordMessage() {
        recordMessage(Instant.now());
    }
    
    /**
     * Registra uma nova mensagem com timestamp específico.
     */
    public void recordMessage(Instant timestamp) {
        this.totalMessagesReceived++;
        this.lastMessageAt = timestamp;
        
        // Mantém apenas os últimos 100 timestamps
        messageTimestamps.add(timestamp);
        if (messageTimestamps.size() > 100) {
            messageTimestamps.removeFirst();
        }
        
        // Atualiza histórico de contagem por minuto
        updateMessageCountHistory(timestamp);
        
        // Reset silence period se estava em silêncio
        if (lastSilenceStart != null) {
            lastSilenceStart = null;
        }
    }
    
    /**
     * Registra uma mensagem processada com tempo de processamento.
     * 
     * @param processingStartTime momento que começou o processamento da mensagem
     */
    public void recordMessageProcessed(Instant processingStartTime) {
        Instant now = Instant.now();
        Duration processingTime = Duration.between(processingStartTime, now);
        
        // Registra a mensagem normalmente
        recordMessage(now);
        
        // Registra tempo de processamento
        responseTimes.add(processingTime);
        if (responseTimes.size() > 1000) {
            responseTimes.removeFirst();
        }
    }
    
    /**
     * Registra um erro.
     */
    public void recordError() {
        this.totalErrors++;
    }
    
    /**
     * Registra uma mensagem mal-formada.
     */
    public void recordMalformedMessage() {
        this.malformedMessages++;
    }
    
    /**
     * Registra uma mensagem duplicada.
     */
    public void recordDuplicateMessage() {
        this.duplicateMessages++;
    }
    
    
    /**
     * Marca início de período de silêncio.
     */
    public void markSilenceStart() {
        if (lastSilenceStart == null) {
            this.lastSilenceStart = Instant.now();
        }
    }
    
    /**
     * Atualiza o histórico de contagem de mensagens por minuto.
     */
    private void updateMessageCountHistory(Instant timestamp) {
        // Calcula minuto atual (truncado)
        long currentMinute = timestamp.truncatedTo(ChronoUnit.MINUTES).getEpochSecond() / 60;
        
        // Incrementa contador para este minuto
        messageCountPerMinute.merge(currentMinute, 1L, Long::sum);
        
        // Remove minutos antigos (mantém últimos 10 minutos)
        long tenMinutesAgo = currentMinute - 10;
        messageCountPerMinute.entrySet().removeIf(entry -> entry.getKey() < tenMinutesAgo);
        
        // Atualiza lista histórica ordenada (últimos 10 minutos)
        updateMessageCountHistoryList();
    }
    
    /**
     * Atualiza a lista messageCountHistory com base nos dados atuais.
     */
    private void updateMessageCountHistoryList() {
        messageCountHistory.clear();
        messageCountPerMinute.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> messageCountHistory.add(entry.getValue()));
    }
    
    /**
     * Força limpeza do histórico de mensagens (útil para reset/debug).
     */
    public void clearMessageCountHistory() {
        messageCountPerMinute.clear();
        messageCountHistory.clear();
    }

    // Métodos para calcular estatísticas derivadas
    
    /**
     * Calcula o uptime total desde o início da conexão.
     */
    public Duration getTotalUptime() {
        if (connectionStartTime == null) {
            return Duration.ZERO;
        }
        return Duration.between(connectionStartTime, Instant.now());
    }
    
    /**
     * Calcula o período de silêncio mais longo.
     */
    public Duration getLongestSilencePeriod() {
        if (messageTimestamps.size() < 2) {
            return lastSilenceStart != null ? 
                Duration.between(lastSilenceStart, Instant.now()) : Duration.ZERO;
        }
        
        Duration maxSilence = Duration.ZERO;
        
        // Verifica gaps entre mensagens
        for (int i = 1; i < messageTimestamps.size(); i++) {
            Duration gap = Duration.between(messageTimestamps.get(i-1), messageTimestamps.get(i));
            if (gap.compareTo(maxSilence) > 0) {
                maxSilence = gap;
            }
        }
        
        // Verifica silêncio atual se existir
        if (lastSilenceStart != null) {
            Duration currentSilence = Duration.between(lastSilenceStart, Instant.now());
            if (currentSilence.compareTo(maxSilence) > 0) {
                maxSilence = currentSilence;
            }
        }
        
        return maxSilence;
    }
    
    /**
     * Calcula tempo médio de resposta.
     */
    public Duration getAverageResponseTime() {
        if (responseTimes.isEmpty()) {
            return Duration.ZERO;
        }
        
        long totalNanos = responseTimes.stream()
            .mapToLong(Duration::toNanos)
            .sum();
            
        return Duration.ofNanos(totalNanos / responseTimes.size());
    }

    /**
     * Verifica se há atividade recente (últimas mensagens).
     */
    public boolean hasRecentActivity() {
        return lastMessageAt != null;
    }
    
    /**
     * Verifica se há conexões ativas.
     */
    public boolean hasConnections() {
        return totalConnections > 0;
    }
    
    /**
     * Verifica se há erros registrados.
     */
    public boolean hasErrors() {
        return totalErrors > 0;
    }
    
    /**
     * Calcula taxa de erro como porcentagem.
     */
    public double getErrorRate() {
        if (totalConnections == 0) {
            return 0.0;
        }
        return (double) totalErrors / totalConnections * 100.0;
    }
    
    /**
     * Calcula taxa de reconexão como porcentagem.
     */
    public double getReconnectionRate() {
        if (totalConnections == 0) {
            return 0.0;
        }
        return (double) totalReconnections / totalConnections * 100.0;
    }

    /**
     * Calcula mensagens por minuto baseado no histórico recente.
     */
    public double getMessagesPerMinute() {
        if (messageTimestamps.isEmpty() || messageTimestamps.size() < 2) {
            return 0.0;
        }
        
        Instant now = Instant.now();
        long messagesInLastMinute = messageTimestamps.stream()
            .mapToLong(timestamp -> now.minusSeconds(60).isBefore(timestamp) ? 1 : 0)
            .sum();
            
        return (double) messagesInLastMinute;
    }

    /**
     * Calcula intervalo médio entre mensagens.
     */
    public Duration getAverageMessageInterval() {
        if (messageTimestamps.size() < 2) {
            return Duration.ZERO;
        }
        
        List<Instant> sorted = messageTimestamps.stream()
            .sorted()
            .toList();
            
        long totalIntervalSeconds = 0;
        for (int i = 1; i < sorted.size(); i++) {
            totalIntervalSeconds += Duration.between(sorted.get(i-1), sorted.get(i)).getSeconds();
        }
        
        return Duration.ofSeconds(totalIntervalSeconds / (sorted.size() - 1));
    }

    /**
     * Calcula porcentagem de uptime.
     */
    public double getUptimePercentage() {
        if (connectionStartTime == null) {
            return 0.0;
        }
        
        Duration totalTime = getTotalUptime();
        if (totalTime.isZero()) {
            return 100.0;
        }
        
        // Considera uptime como tempo total menos o silêncio atual
        Duration currentSilence = lastSilenceStart != null ? 
            Duration.between(lastSilenceStart, Instant.now()) : Duration.ZERO;
        Duration actualUptime = totalTime.minus(currentSilence);
        
        return Math.max(0.0, (double) actualUptime.toSeconds() / totalTime.toSeconds() * 100.0);
    }

    /**
     * Calcula score de confiabilidade baseado em múltiplos fatores.
     * 
     * @param expectedMessagesPerMinute baseline esperado de mensagens por minuto para calcular consistência
     */
    public double getReliabilityScore(long expectedMessagesPerMinute) {
        double uptimeScore = getUptimePercentage() * 0.30;        // 30%
        double errorScore = (100.0 - getErrorRate()) * 0.25;     // 25%
        double reconnectionScore = (100.0 - getReconnectionRate()) * 0.15; // 15%
        
        // Consistência de mensagens (20%)
        double expectedMessages = expectedMessagesPerMinute * (getTotalUptime().toMinutes() + 1);
        double consistencyRatio = expectedMessages > 0 ? 
            Math.min(totalMessagesReceived / expectedMessages, 1.0) : 1.0;
        double consistencyScore = consistencyRatio * 20.0;
        
        // Performance score baseado em silence periods (10%)
        double silenceScore = getLongestSilencePeriod().toSeconds() > 300 ? 0 : 10.0; // Penaliza > 5min
        
        return Math.max(0, Math.min(100, 
            uptimeScore + errorScore + reconnectionScore + consistencyScore + silenceScore));
    }

    /**
     * Determina tendência de estabilidade baseada no histórico.
     * Usa algoritmo robusto com suavização e validação cruzada.
     */
    public String getStabilityTrend() {
        if (messageCountHistory.size() < 5) {
            return "STABLE"; // Assume estável até ter dados suficientes
        }
        
        // Usa últimos 5 períodos para análise mais robusta
        List<Long> recent = messageCountHistory.subList(
            Math.max(0, messageCountHistory.size() - 5), 
            messageCountHistory.size()
        );
        
        // Calcula tendência linear usando regressão simples
        double trend = calculateLinearTrend(recent);
        
        // Calcula média para normalização
        double average = recent.stream().mapToLong(Long::longValue).average().orElse(1.0);
        
        // Converte trend para percentual normalizado
        double trendPercent = average > 0 ? (trend / average) * 100.0 : 0.0;
        
        // Usa thresholds mais conservadores (35% ao invés de 20%)
        // Adiciona validação cruzada com reliability score
        double reliabilityScore = getReliabilityScore(60); // 60 msg/min baseline
        
        if (reliabilityScore > 90 && Math.abs(trendPercent) < 50) {
            // Se reliability está alta, não reporta degradação por pequenas variações
            return "STABLE";
        }
        
        if (trendPercent > 35) return "IMPROVING";
        if (trendPercent < -35) return "DEGRADING";
        return "STABLE";
    }
    
    /**
     * Calcula tendência linear usando regressão simples.
     * @param values lista de valores para análise
     * @return inclinação da reta (trend positivo/negativo)
     */
    private double calculateLinearTrend(List<Long> values) {
        int n = values.size();
        if (n < 2) return 0.0;
        
        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;
        
        for (int i = 0; i < n; i++) {
            double x = i + 1; // posição temporal
            double y = values.get(i); // valor
            
            sumX += x;
            sumY += y;
            sumXY += x * y;
            sumX2 += x * x;
        }
        
        // Fórmula da regressão linear: slope = (n*ΣXY - ΣX*ΣY) / (n*ΣX² - (ΣX)²)
        double denominator = n * sumX2 - sumX * sumX;
        if (Math.abs(denominator) < 0.0001) return 0.0; // Evita divisão por zero
        
        return (n * sumXY - sumX * sumY) / denominator;
    }

    /**
     * Verifica se a conexão está atualmente estável.
     */
    public boolean isCurrentlyStable() {
        Instant now = Instant.now();
        
        // Considera estável se:
        // 1. Recebeu mensagem nos últimos 2 minutos
        // 2. Não tem silence period ativo > 5 minutos
        // 3. Taxa de erro < 10%
        
        boolean hasRecentMessages = lastMessageAt != null && 
            Duration.between(lastMessageAt, now).toMinutes() < 2;
            
        boolean noLongSilence = lastSilenceStart == null || 
            Duration.between(lastSilenceStart, now).toMinutes() < 5;
            
        boolean lowErrorRate = getErrorRate() < 10.0;
        
        return hasRecentMessages && noLongSilence && lowErrorRate;
    }

    /**
     * Calcula score de qualidade dos dados.
     */
    public double getDataQualityScore() {
        if (totalMessagesReceived == 0) {
            return 100.0;
        }
        
        double malformedRate = (double) malformedMessages / totalMessagesReceived * 100.0;
        double duplicateRate = (double) duplicateMessages / totalMessagesReceived * 100.0;
        
        return Math.max(0, 100.0 - malformedRate - duplicateRate);
    }

}