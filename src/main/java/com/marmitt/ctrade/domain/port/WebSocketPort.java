package com.marmitt.ctrade.domain.port;

public interface WebSocketPort {
    
    void connect();
    
    void disconnect();
    
    boolean isConnected();
    
    void subscribeToPrice(String tradingPair);
    
    void subscribeToOrderUpdates();
}