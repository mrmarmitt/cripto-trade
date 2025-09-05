package com.marmitt.core.ports.outbound;

import com.marmitt.core.domain.MarketData;
import com.marmitt.core.domain.Symbol;

import java.util.concurrent.CompletableFuture;

public interface MarketDataPort {
    
    CompletableFuture<MarketData> getCurrentMarketData(Symbol symbol);
    
    void subscribeToMarketData(Symbol symbol, MarketDataListener listener);
    
    void unsubscribeFromMarketData(Symbol symbol);
    
    boolean isSubscribed(Symbol symbol);
    
    CompletableFuture<Void> connect();
    
    CompletableFuture<Void> disconnect();
    
    boolean isConnected();
    
    @FunctionalInterface
    interface MarketDataListener {
        void onMarketDataUpdate(MarketData marketData);
    }
}