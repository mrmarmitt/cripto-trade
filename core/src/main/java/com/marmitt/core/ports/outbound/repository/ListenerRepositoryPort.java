package com.marmitt.core.ports.outbound.repository;

import com.marmitt.core.ports.outbound.listener.OrderUpdateListener;
import com.marmitt.core.ports.outbound.listener.PriceUpdateListener;

import java.util.List;

public interface ListenerRepositoryPort {

    boolean addOrderUpdateListener(OrderUpdateListener listener);

    boolean removeOrderUpdateListener(OrderUpdateListener listener);

    List<OrderUpdateListener> getAllOrderUpdateListeners();

    boolean addPriceUpdateListener(PriceUpdateListener listener);

    boolean removePriceUpdateListener(PriceUpdateListener listener);

    List<PriceUpdateListener> getAllPriceUpdateListeners();

    int getOrderUpdateListenerCount();

    int getPriceUpdateListenerCount();

    void clearAllListeners();
}