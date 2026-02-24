package com.marmitt.application.spring.repository;

import com.marmitt.core.application.listener.portfolio.PortfolioOrderUpdateListener;
import com.marmitt.core.application.listener.portfolio.PortfolioStrategyListener;
import com.marmitt.core.ports.outbound.listener.OrderUpdateListener;
import com.marmitt.core.ports.outbound.listener.PriceUpdateListener;
import com.marmitt.core.ports.outbound.repository.ExchangeAdapterRepositoryPort;
import com.marmitt.core.ports.outbound.repository.ListenerRepositoryPort;
import com.marmitt.core.application.listener.MarketDataPriceUpdateListener;
import com.marmitt.core.application.listener.TradingOrderUpdateListener;
import com.marmitt.core.ports.outbound.repository.PortfolioRepositoryPort;
import com.marmitt.core.ports.outbound.repository.StrategyRepositoryPort;
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

    private final PortfolioRepositoryPort portfolioRepository;
    private final StrategyRepositoryPort strategyRepository;
    private final ExchangeAdapterRepositoryPort exchangeAdapterRepository;

    public InMemoryListenerRepository(
            PortfolioRepositoryPort portfolioRepository,
            StrategyRepositoryPort strategyRepository,
            ExchangeAdapterRepositoryPort exchangeAdapterRepository) {

        this.exchangeAdapterRepository = exchangeAdapterRepository;

        this.orderUpdateListeners = new ConcurrentHashMap<>();
        this.priceUpdateListeners = new ConcurrentHashMap<>();

        this.portfolioRepository = portfolioRepository;
        this.strategyRepository = strategyRepository;
    }

    @PostConstruct
    public void init() {
        // Listeners de logging/debug
        TradingOrderUpdateListener tradingOrderUpdateListener = new TradingOrderUpdateListener();
        MarketDataPriceUpdateListener marketDataPriceUpdateListener = new MarketDataPriceUpdateListener();

        // Listeners de Portfolio - independentes, comunicam via PortfolioRepository
        PortfolioStrategyListener portfolioStrategyListener = new PortfolioStrategyListener(
                portfolioRepository,
                strategyRepository,
                exchangeAdapterRepository);

        PortfolioOrderUpdateListener portfolioOrderUpdateListener = new PortfolioOrderUpdateListener(
                portfolioRepository);

        // OrderUpdateListeners
        addOrderUpdateListener(tradingOrderUpdateListener);
        addOrderUpdateListener(portfolioOrderUpdateListener);  // Processa ordens FILLED - atualiza portfolio

        // PriceUpdateListeners
        addPriceUpdateListener(marketDataPriceUpdateListener);
        addPriceUpdateListener(portfolioStrategyListener);  // Executa estratégias

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