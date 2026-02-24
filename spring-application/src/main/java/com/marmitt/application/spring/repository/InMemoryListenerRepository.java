package com.marmitt.application.spring.repository;

import com.marmitt.core.application.listener.MarketDataPriceUpdateListener;
import com.marmitt.core.application.listener.runner.PortfolioStrategyRunnerOrderUpdateListener;
import com.marmitt.core.application.listener.runner.PortfolioStrategyRunnerPriceUpdateListener;
import com.marmitt.core.application.usecase.runner.OrderConciliationUseCase;
import com.marmitt.core.application.usecase.runner.processsignal.ProcessTradeSignalUseCase;
import com.marmitt.core.ports.outbound.listener.OrderUpdateListener;
import com.marmitt.core.ports.outbound.listener.PriceUpdateListener;
import com.marmitt.core.ports.outbound.repository.ListenerRepositoryPort;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementação em memória do repositório de listeners.
 * Utiliza Maps thread-safe para armazenar listeners registrados.
 */
@Slf4j
@Repository
public class InMemoryListenerRepository implements ListenerRepositoryPort {

    private final Map<String, OrderUpdateListener> orderUpdateListeners;
    private final Map<String, PriceUpdateListener> priceUpdateListeners;

    private final ProcessTradeSignalUseCase newProcessTradeSignal;
    private final OrderConciliationUseCase orderConciliation;

    public InMemoryListenerRepository(
            ProcessTradeSignalUseCase newProcessTradeSignal,
            OrderConciliationUseCase orderConciliation) {

        this.newProcessTradeSignal = newProcessTradeSignal;
        this.orderConciliation = orderConciliation;

        this.orderUpdateListeners = new ConcurrentHashMap<>();
        this.priceUpdateListeners = new ConcurrentHashMap<>();
    }

    @PostConstruct
    public void init() {
        PortfolioStrategyRunnerPriceUpdateListener runnerPriceUpdateListener =
                new PortfolioStrategyRunnerPriceUpdateListener(newProcessTradeSignal);

        PortfolioStrategyRunnerOrderUpdateListener runnerOrderUpdateListener =
                new PortfolioStrategyRunnerOrderUpdateListener(orderConciliation);

        addOrderUpdateListener(runnerOrderUpdateListener);

        addPriceUpdateListener(new MarketDataPriceUpdateListener());
        addPriceUpdateListener(runnerPriceUpdateListener);

        log.info("Listeners initialized - OrderUpdateListeners: {}, PriceUpdateListeners: {}",
                orderUpdateListeners.size(), priceUpdateListeners.size());
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

    private String generateListenerKey(Object listener) {
        return listener.getClass().getSimpleName() + "_" + listener.hashCode();
    }
}
