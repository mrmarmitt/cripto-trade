package com.marmitt.core.domain;

import java.time.Instant;

/**
 * Domain object representando estatísticas de conexão WebSocket.
 * 
 * Contém métricas agregadas de conexões, mensagens e erros
 * para uma exchange específica ou globalmente.
 */
public record ConnectionStats(
    long totalConnections,
    long totalReconnections,
    long totalMessagesReceived,
    long totalErrors,
    Instant lastConnectedAt,
    Instant lastMessageAt
) {
    
    /**
     * Cria estatísticas zeradas para uma exchange.
     */
    public static ConnectionStats empty() {
        return new ConnectionStats(0, 0, 0, 0, null, null);
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
}