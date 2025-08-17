package com.marmitt.ctrade.infrastructure.websocket;

import com.marmitt.ctrade.domain.dto.OrderUpdateMessage;
import com.marmitt.ctrade.domain.dto.PriceUpdateMessage;
import com.marmitt.ctrade.domain.port.ExchangeWebSocketAdapter;
import com.marmitt.ctrade.domain.port.WebSocketPort;
import com.marmitt.ctrade.infrastructure.config.WebSocketProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.util.Set;

/**
 * Classe abstrata base para todos os adapters WebSocket.
 * 
 * Fornece:
 * - Template methods para implementações específicas
 * - Orquestração entre serviços especializados
 * - Interface unificada para todas as exchanges
 * - Delegação de responsabilidades para serviços dedicados
 */
@Slf4j
public abstract class AbstractWebSocketAdapter implements WebSocketPort, ExchangeWebSocketAdapter {
    
    // Specialized services
    @Autowired
    protected WebSocketEventPublisher eventPublisher;
    @Autowired 
    protected ConnectionManager connectionManager;
    @Autowired
    protected ConnectionStatsTracker statsTracker;
    
    // Infrastructure dependencies
    protected final WebSocketProperties properties;
    protected final ReconnectionStrategy reconnectionStrategy;
    protected final WebSocketCircuitBreaker circuitBreaker;
    
    protected AbstractWebSocketAdapter(WebSocketProperties properties,
                                     ReconnectionStrategy reconnectionStrategy,
                                     WebSocketCircuitBreaker circuitBreaker) {
        this.properties = properties;
        this.reconnectionStrategy = reconnectionStrategy;
        this.circuitBreaker = circuitBreaker;
    }
    
    /**
     * Template method para processar atualizações de preço.
     * Implementações específicas devem sobrescrever este método.
     */
    protected void onPriceUpdate(PriceUpdateMessage priceUpdate) {
        statsTracker.recordMessageReceived();
        eventPublisher.publishPriceUpdate(this, priceUpdate, getExchangeName());
    }
    
    /**
     * Template method para processar atualizações de ordem.
     * Implementações específicas devem sobrescrever este método.
     */
    protected void onOrderUpdate(OrderUpdateMessage orderUpdate) {
        statsTracker.recordMessageReceived();
        eventPublisher.publishOrderUpdate(this, orderUpdate, getExchangeName());
    }
    
    /**
     * Retorna o nome da exchange para identificação nos eventos.
     * Deve ser implementado pelas classes filhas.
     */
    public abstract String getExchangeName();
    
    // ========== ExchangeWebSocketAdapter Implementation ==========
    
    @Override
    public ConnectionStatus getConnectionStatus() {
        return connectionManager.getStatus();
    }
    
    @Override
    public Set<String> getSubscribedPairs() {
        return connectionManager.getSubscribedPairs();
    }
    
    @Override
    public boolean isOrderUpdatesSubscribed() {
        return connectionManager.isOrderUpdatesSubscribed();
    }
    
    @Override
    public ConnectionStats getConnectionStats() {
        return statsTracker.getStats();
    }
    
    @Override
    public void forceReconnect() {
        connectionManager.forceReconnect(
            Duration.ofSeconds(1),
            reconnectionStrategy,
            statsTracker,
            this::doDisconnect,
            this::doConnect,
            getExchangeName()
        );
    }
    
    @Override
    public boolean isConnected() {
        return connectionManager.isConnected();
    }
    
    // ========== Protected Helper Methods ==========
    
    /**
     * Agenda reconexão usando o ConnectionManager.
     */
    protected void scheduleReconnection(Duration delay) {
        connectionManager.scheduleReconnection(
            delay,
            reconnectionStrategy,
            statsTracker,
            this::doConnect,
            getExchangeName()
        );
    }
    
    /**
     * Métodos delegados para o ConnectionManager e StatsTracker
     */
    protected void updateConnectionStatus(ConnectionStatus newStatus) {
        connectionManager.updateStatus(newStatus);
    }
    
    protected void recordConnection() {
        statsTracker.recordConnection();
    }
    
    protected void recordError() {
        statsTracker.recordError();
    }
    
    // ========== Template Methods for Subclasses ==========
    
    /**
     * Template method para realizar a conexão específica da exchange.
     * Subclasses devem implementar a lógica específica de conexão.
     */
    protected abstract void doConnect();
    
    /**
     * Template method para realizar a desconexão específica da exchange.
     * Subclasses devem implementar a lógica específica de desconexão.
     */
    protected abstract void doDisconnect();
    
    /**
     * Template method para implementar subscrição específica da exchange.
     */
    protected abstract void doSubscribeToPrice(String tradingPair);
    
    /**
     * Template method para implementar subscrição de ordens específica da exchange.
     */
    protected abstract void doSubscribeToOrderUpdates();
    
    // ========== Final Implementation of Base Methods ==========
    
    @Override
    public final void connect() {
        if (!connectionManager.canConnect(circuitBreaker)) {
            log.warn("Circuit breaker is OPEN, cannot connect to {} WebSocket", getExchangeName());
            return;
        }
        
        if (connectionManager.isConnectingOrConnected()) {
            log.debug("Already connecting or connected to {} WebSocket", getExchangeName());
            return;
        }
        
        connectionManager.updateStatus(ConnectionStatus.CONNECTING);
        log.info("Connecting to {} WebSocket: {}", getExchangeName(), properties.getUrl());
        
        recordConnection();
        doConnect();
    }
    
    @Override
    public final void disconnect() {
        connectionManager.updateStatus(ConnectionStatus.DISCONNECTED);
        connectionManager.resetSubscriptions();
        connectionManager.cancelReconnectionTask();
        
        doDisconnect();
        
        log.info("Disconnected from {} WebSocket", getExchangeName());
    }
    
    @Override
    public final void subscribeToPrice(String tradingPair) {
        if (!connectionManager.isConnected()) {
            log.warn("Cannot subscribe to {}: {} WebSocket not connected", tradingPair, getExchangeName());
            return;
        }
        
        connectionManager.addSubscription(tradingPair);
        log.info("Subscribed to price updates for {} on {}", tradingPair, getExchangeName());
        
        doSubscribeToPrice(tradingPair);
    }
    
    @Override
    public final void subscribeToOrderUpdates() {
        if (!connectionManager.isConnected()) {
            log.warn("Cannot subscribe to order updates: {} WebSocket not connected", getExchangeName());
            return;
        }
        
        connectionManager.subscribeToOrderUpdates();
        log.info("Subscribed to order updates on {}", getExchangeName());
        
        doSubscribeToOrderUpdates();
    }
}