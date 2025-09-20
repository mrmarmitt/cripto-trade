package com.marmitt.repository;

import com.marmitt.core.ports.outbound.listener.OrderUpdateListener;
import com.marmitt.core.ports.outbound.listener.PriceUpdateListener;
import com.marmitt.core.ports.outbound.repository.ListenerRepositoryPort;
import com.marmitt.listener.MarketDataPriceUpdateListener;
import com.marmitt.listener.TradingOrderUpdateListener;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementação em memória do repositório de listeners.
 * Utiliza Maps thread-safe para armazenar listeners registrados.
 */
@Repository
public class InMemoryListenerRepository implements ListenerRepositoryPort {
    
    private final Map<String, OrderUpdateListener> orderUpdateListeners = new ConcurrentHashMap<>();
    private final Map<String, PriceUpdateListener> priceUpdateListeners = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        TradingOrderUpdateListener tradingOrderUpdateListener = new TradingOrderUpdateListener();
        MarketDataPriceUpdateListener marketDataPriceUpdateListener = new MarketDataPriceUpdateListener();

        addOrderUpdateListener(tradingOrderUpdateListener);
        addPriceUpdateListener(marketDataPriceUpdateListener);
    }

    @Override
    public boolean addOrderUpdateListener(OrderUpdateListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("OrderUpdateListener cannot be null");
        }
        
        String key = generateListenerKey(listener);
        return orderUpdateListeners.put(key, listener) == null;
    }
    
    @Override
    public boolean removeOrderUpdateListener(OrderUpdateListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("OrderUpdateListener cannot be null");
        }
        
        String key = generateListenerKey(listener);
        return orderUpdateListeners.remove(key) != null;
    }
    
    @Override
    public List<OrderUpdateListener> getAllOrderUpdateListeners() {
        return List.copyOf(orderUpdateListeners.values());
    }
    
    @Override
    public boolean addPriceUpdateListener(PriceUpdateListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("PriceUpdateListener cannot be null");
        }
        
        String key = generateListenerKey(listener);
        return priceUpdateListeners.put(key, listener) == null;
    }
    
    @Override
    public boolean removePriceUpdateListener(PriceUpdateListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("PriceUpdateListener cannot be null");
        }
        
        String key = generateListenerKey(listener);
        return priceUpdateListeners.remove(key) != null;
    }
    
    @Override
    public List<PriceUpdateListener> getAllPriceUpdateListeners() {
        return List.copyOf(priceUpdateListeners.values());
    }
    
    @Override
    public int getOrderUpdateListenerCount() {
        return orderUpdateListeners.size();
    }
    
    @Override
    public int getPriceUpdateListenerCount() {
        return priceUpdateListeners.size();
    }
    
    @Override
    public void clearAllListeners() {
        orderUpdateListeners.clear();
        priceUpdateListeners.clear();
    }
    
    /**
     * Gera uma chave única para o listener baseada na classe e hashCode.
     * 
     * @param listener listener para gerar a chave
     * @return chave única do listener
     */
    private String generateListenerKey(Object listener) {
        return listener.getClass().getSimpleName() + "_" + listener.hashCode();
    }
}