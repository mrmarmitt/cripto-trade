package com.marmitt.core.dto.listener;

/**
 * Record que contém estatísticas sobre listeners registrados.
 */
public record ListenerStats(
    int orderUpdateListenerCount,
    int priceUpdateListenerCount,
    int totalListenerCount
) {
    
    public ListenerStats(int orderUpdateListenerCount, int priceUpdateListenerCount) {
        this(orderUpdateListenerCount, priceUpdateListenerCount, orderUpdateListenerCount + priceUpdateListenerCount);
    }
}