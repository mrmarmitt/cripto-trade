package com.marmitt.core.application.usecase;

import com.marmitt.core.dto.listener.ListenerStats;
import com.marmitt.core.ports.inbound.listener.ManageListenersPort;
import com.marmitt.core.ports.outbound.listener.OrderUpdateListener;
import com.marmitt.core.ports.outbound.listener.PriceUpdateListener;
import com.marmitt.core.ports.outbound.repository.ListenerRepositoryPort;

import java.util.List;

/**
 * UseCase para gerenciamento de listeners.
 * Coordena operações de registro, remoção e consulta de listeners
 * utilizando o repositório em memória.
 */
public class ManageListenersUseCase implements ManageListenersPort {
    
    private final ListenerRepositoryPort listenerRepository;
    
    public ManageListenersUseCase(ListenerRepositoryPort listenerRepository) {
        this.listenerRepository = listenerRepository;
    }
    
    @Override
    public boolean registerOrderUpdateListener(OrderUpdateListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("OrderUpdateListener cannot be null");
        }
        return listenerRepository.addOrderUpdateListener(listener);
    }
    
    @Override
    public boolean unregisterOrderUpdateListener(OrderUpdateListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("OrderUpdateListener cannot be null");
        }
        return listenerRepository.removeOrderUpdateListener(listener);
    }
    
    @Override
    public boolean registerPriceUpdateListener(PriceUpdateListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("PriceUpdateListener cannot be null");
        }
        return listenerRepository.addPriceUpdateListener(listener);
    }
    
    @Override
    public boolean unregisterPriceUpdateListener(PriceUpdateListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("PriceUpdateListener cannot be null");
        }
        return listenerRepository.removePriceUpdateListener(listener);
    }
    
    @Override
    public List<OrderUpdateListener> getAllOrderUpdateListeners() {
        return listenerRepository.getAllOrderUpdateListeners();
    }
    
    @Override
    public List<PriceUpdateListener> getAllPriceUpdateListeners() {
        return listenerRepository.getAllPriceUpdateListeners();
    }
    
    @Override
    public ListenerStats getListenerStats() {
        int orderListenerCount = listenerRepository.getOrderUpdateListenerCount();
        int priceListenerCount = listenerRepository.getPriceUpdateListenerCount();
        
        return new ListenerStats(orderListenerCount, priceListenerCount);
    }
    
    @Override
    public void clearAllListeners() {
        listenerRepository.clearAllListeners();
    }
}