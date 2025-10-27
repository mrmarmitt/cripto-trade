package com.marmitt.core.dto.listener;

public record ListenerStats(
    int orderUpdateListenerCount,
    int priceUpdateListenerCount,
    int totalListenerCount
) {
    
    public ListenerStats(int orderUpdateListenerCount, int priceUpdateListenerCount) {
        this(orderUpdateListenerCount, priceUpdateListenerCount, orderUpdateListenerCount + priceUpdateListenerCount);
    }
}