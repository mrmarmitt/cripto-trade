package com.marmitt.ctrade.infrastructure.websocket;

import com.marmitt.ctrade.domain.port.ExchangeWebSocketAdapter.ConnectionStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Serviço responsável pelo gerenciamento genérico de eventos de conexão WebSocket.
 * 
 * Centraliza a lógica comum de todos os listeners WebSocket, incluindo:
 * - Atualização de status de conexão
 * - Controle de reconexão e circuit breaker
 * - Atualização de estatísticas
 * - Decisões sobre reconexão automática
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WebSocketConnectionHandler {
    
    private final ConnectionManager connectionManager;
    private final ConnectionStatsTracker statsTracker;
    private final WebSocketCircuitBreaker circuitBreaker;
    private final ReconnectionStrategy reconnectionStrategy;
    
    /**
     * Manipula evento de conexão bem-sucedida.
     */
    public void handleConnectionOpened(String exchangeName) {
        
        connectionManager.updateStatus(ConnectionStatus.CONNECTED);
        statsTracker.updateLastConnectedAt(LocalDateTime.now());
        reconnectionStrategy.reset();
        circuitBreaker.recordSuccess();
        
        log.info("Connected to {} WebSocket successfully", exchangeName);
    }
    
    /**
     * Manipula evento de mensagem recebida (atualiza estatísticas).
     */
    public void handleMessageReceived() {
        statsTracker.recordMessageReceived();
    }
    
    /**
     * Manipula evento de erro durante processamento.
     */
    public void handleProcessingError(String exchangeName, Exception error) {
        statsTracker.recordError();
        log.error("Error processing {} WebSocket message: {}", exchangeName, error.getMessage(), error);
    }
    
    /**
     * Manipula evento de conexão sendo fechada.
     */
    public void handleConnectionClosing(String exchangeName, int code, String reason) {
        log.warn("{} WebSocket connection closing: {} - {}", exchangeName, code, reason);
    }
    
    /**
     * Manipula evento de conexão fechada.
     * Retorna true se deve tentar reconectar.
     */
    public boolean handleConnectionClosed(String exchangeName, 
                                        int code, 
                                        String reason,
                                        Runnable scheduleReconnectionCallback) {
        
        connectionManager.updateStatus(ConnectionStatus.DISCONNECTED);
        log.warn("{} WebSocket connection closed: {} - {}", exchangeName, code, reason);
        
        // Schedule reconnection if not manually disconnected
        boolean shouldReconnect = code != 1000; // 1000 = normal closure
        if (shouldReconnect) {
            circuitBreaker.recordFailure();
            scheduleReconnectionCallback.run();
        }
        
        return shouldReconnect;
    }
    
    /**
     * Manipula evento de falha na conexão.
     */
    public void handleConnectionFailure(String exchangeName,
                                      Throwable error,
                                      Runnable scheduleReconnectionCallback) {
        
        connectionManager.updateStatus(ConnectionStatus.FAILED);
        statsTracker.recordError();
        circuitBreaker.recordFailure();
        
        log.error("{} WebSocket connection failed: {}", exchangeName, error.getMessage(), error);
        
        scheduleReconnectionCallback.run();
    }
}