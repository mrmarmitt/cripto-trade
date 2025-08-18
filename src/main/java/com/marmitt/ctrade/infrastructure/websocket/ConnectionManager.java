package com.marmitt.ctrade.infrastructure.websocket;

import com.marmitt.ctrade.domain.port.ExchangeWebSocketAdapter.ConnectionStatus;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * Serviço responsável pelo gerenciamento de conexões WebSocket.
 * 
 * Centraliza o controle de estado de conexão, reconexão automática,
 * circuit breaker e gerenciamento de subscrições.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ConnectionManager {
    
    private final TaskScheduler taskScheduler;
    private final ConnectionStatsTracker statsTracker;
    private final WebSocketCircuitBreaker circuitBreaker;
    private final ReconnectionStrategy reconnectionStrategy;

    /**
     * -- GETTER --
     *  Retorna o status atual da conexão.
     */
    // Connection state
    @Getter
    private volatile ConnectionStatus status = ConnectionStatus.DISCONNECTED;
    private final Set<String> subscribedPairs = ConcurrentHashMap.newKeySet();
    /**
     * -- GETTER --
     *  Verifica se order updates estão subscritas.
     */
    @Getter
    private volatile boolean orderUpdatesSubscribed = false;
    private volatile ScheduledFuture<?> reconnectionTask;
    
    /**
     * Verifica se pode conectar baseado no circuit breaker.
     */
    public boolean canConnect() {
        boolean canConnect = circuitBreaker.canConnect();
        if (!canConnect) {
            log.warn("Circuit breaker is OPEN, cannot connect");
        }
        return canConnect;
    }
    
    /**
     * Verifica se a conexão está em estado de conectado ou conectando.
     */
    public boolean isConnectingOrConnected() {
        return status == ConnectionStatus.CONNECTING || status == ConnectionStatus.CONNECTED;
    }
    
    /**
     * Atualiza o status da conexão.
     */
    public void updateStatus(ConnectionStatus newStatus) {
        ConnectionStatus oldStatus = this.status;
        this.status = newStatus;
        if (oldStatus != newStatus) {
            log.debug("Connection status changed from {} to {}", oldStatus, newStatus);
        }
    }

    /**
     * Verifica se está conectado.
     */
    public boolean isConnected() {
        return status == ConnectionStatus.CONNECTED;
    }
    
    /**
     * Adiciona trading pair às subscrições.
     */
    public void addSubscription(String tradingPair) {
        subscribedPairs.add(tradingPair);
        log.debug("Added subscription for trading pair: {}", tradingPair);
    }
    
    /**
     * Marca order updates como subscrito.
     */
    public void subscribeToOrderUpdates() {
        this.orderUpdatesSubscribed = true;
        log.debug("Subscribed to order updates");
    }
    
    /**
     * Retorna cópia das trading pairs subscritas.
     */
    public Set<String> getSubscribedPairs() {
        return Set.copyOf(subscribedPairs);
    }

    /**
     * Reseta todas as subscrições.
     */
    public void resetSubscriptions() {
        subscribedPairs.clear();
        orderUpdatesSubscribed = false;
        log.debug("All subscriptions reset");
    }
    
    /**
     * Agenda reconexão automática.
     */
    public void scheduleReconnectionBasedOnStrategy(Runnable connectAction, String exchangeName) {
        scheduleReconnection(reconnectionStrategy.getNextDelay(), connectAction, exchangeName);
    }

    /**
     * Cancela task de reconexão pendente.
     */
    public void cancelReconnectionTask() {
        if (reconnectionTask != null && !reconnectionTask.isCancelled()) {
            reconnectionTask.cancel(false);
            reconnectionTask = null;
            log.debug("Reconnection task cancelled");
        }
    }
    
    /**
     * Força uma reconexão imediata.
     */
    public void forceReconnect(Duration delay,
                              Runnable disconnectAction,
                              Runnable connectAction,
                              String exchangeName) {
        
        log.info("Force reconnection requested for {}", exchangeName);
        disconnectAction.run();
        scheduleReconnection(delay, connectAction, exchangeName);
    }

    private void scheduleReconnection(Duration delay, Runnable connectAction, String exchangeName) {

        if (reconnectionStrategy.shouldReconnect()) {
            updateStatus(ConnectionStatus.RECONNECTING);

            reconnectionTask = taskScheduler.schedule(() -> {
                try {
                    reconnectionStrategy.recordAttempt();
                    statsTracker.recordReconnection();
                    connectAction.run();
                } catch (Exception e) {
                    log.error("Error during reconnection for {}: {}", exchangeName, e.getMessage(), e);
                    updateStatus(ConnectionStatus.FAILED);
                }
            }, java.time.Instant.now().plus(delay));

            log.info("Reconnection scheduled in {} for {}", delay, exchangeName);
        } else {
            updateStatus(ConnectionStatus.FAILED);
            log.error("Max reconnection attempts reached for {}, marking as FAILED", exchangeName);
        }
    }

}