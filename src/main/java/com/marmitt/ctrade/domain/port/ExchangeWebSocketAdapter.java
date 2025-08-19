package com.marmitt.ctrade.domain.port;

import java.util.Set;

public interface ExchangeWebSocketAdapter extends WebSocketPort {
    
    /**
     * Get the exchange name (binance, coinbase, etc.)
     */
    String getExchangeName();
    
    /**
     * Get connection status details
     */
    ConnectionStatus getConnectionStatus();
    
    /**
     * Get current subscribed pairs
     */
    Set<String> getSubscribedPairs();
    
    /**
     * Check if order updates are subscribed
     */
    boolean isOrderUpdatesSubscribed();
    
    /**
     * Get connection statistics
     */
    ConnectionStats getConnectionStats();
    
    /**
     * Force reconnection
     */
    void forceReconnect();
    
    enum ConnectionStatus {
        DISCONNECTED,
        CONNECTING, 
        CONNECTED,
        RECONNECTING,
        FAILED
    }
    
    record ConnectionStats(
        long totalConnections,
        long totalReconnections,
        long totalMessagesReceived,
        long totalErrors,
        java.time.LocalDateTime lastConnectedAt,
        java.time.LocalDateTime lastMessageAt
    ) {}
}