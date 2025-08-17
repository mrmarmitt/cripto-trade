package com.marmitt.ctrade.infrastructure.websocket;

import com.marmitt.ctrade.domain.port.ExchangeWebSocketAdapter.ConnectionStats;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Serviço responsável pelo rastreamento de estatísticas de conexão WebSocket.
 * 
 * Mantém contadores e timestamps relacionados à performance e histórico
 * de conexões WebSocket de forma thread-safe.
 */
@Service
@Slf4j
public class ConnectionStatsTracker {
    
    private final AtomicLong totalConnections = new AtomicLong(0);
    private final AtomicLong totalReconnections = new AtomicLong(0);
    private final AtomicLong totalMessagesReceived = new AtomicLong(0);
    private final AtomicLong totalErrors = new AtomicLong(0);
    private volatile LocalDateTime lastConnectedAt;
    private volatile LocalDateTime lastMessageAt;
    
    /**
     * Incrementa contador de conexões e atualiza timestamp.
     */
    public void recordConnection() {
        totalConnections.incrementAndGet();
        lastConnectedAt = LocalDateTime.now();
        log.debug("Connection recorded. Total connections: {}", totalConnections.get());
    }
    
    /**
     * Incrementa contador de reconexões.
     */
    public void recordReconnection() {
        totalReconnections.incrementAndGet();
        log.debug("Reconnection recorded. Total reconnections: {}", totalReconnections.get());
    }
    
    /**
     * Incrementa contador de mensagens recebidas e atualiza timestamp.
     */
    public void recordMessageReceived() {
        totalMessagesReceived.incrementAndGet();
        lastMessageAt = LocalDateTime.now();
    }
    
    /**
     * Incrementa contador de erros.
     */
    public void recordError() {
        totalErrors.incrementAndGet();
        log.debug("Error recorded. Total errors: {}", totalErrors.get());
    }
    
    /**
     * Atualiza timestamp da última conexão.
     */
    public void updateLastConnectedAt(LocalDateTime time) {
        this.lastConnectedAt = time;
    }
    
    /**
     * Atualiza timestamp da última mensagem.
     */
    public void updateLastMessageAt(LocalDateTime time) {
        this.lastMessageAt = time;
    }
    
    /**
     * Retorna estatísticas atuais de conexão.
     */
    public ConnectionStats getStats() {
        return new ConnectionStats(
            totalConnections.get(),
            totalReconnections.get(),
            totalMessagesReceived.get(),
            totalErrors.get(),
            lastConnectedAt,
            lastMessageAt
        );
    }
    
    /**
     * Reseta todas as estatísticas.
     */
    public void reset() {
        totalConnections.set(0);
        totalReconnections.set(0);
        totalMessagesReceived.set(0);
        totalErrors.set(0);
        lastConnectedAt = null;
        lastMessageAt = null;
        log.debug("Connection stats reset");
    }
}