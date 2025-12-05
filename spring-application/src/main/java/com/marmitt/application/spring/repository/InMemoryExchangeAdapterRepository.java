package com.marmitt.application.spring.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marmitt.application.spring.config.exchange.BinanceExchangeAdapter;
import com.marmitt.application.spring.config.exchange.CoinbaseExchangeAdapter;
import com.marmitt.core.ports.outbound.exchange.adapter.ExchangeAdapterPort;
import com.marmitt.core.ports.outbound.events.EventPublisherPort;
import com.marmitt.core.ports.outbound.repository.ExchangeAdapterRepositoryPort;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryExchangeAdapterRepository  implements ExchangeAdapterRepositoryPort {

    private final Map<String, ExchangeAdapterPort> adapters = new ConcurrentHashMap<>();
    private final Map<UUID, String> adapterByPortfolio = new ConcurrentHashMap<>();

    private final EventPublisherPort eventPublisher;
    private final ObjectMapper objectMapper;

    public InMemoryExchangeAdapterRepository(EventPublisherPort eventPublisher, ObjectMapper objectMapper) {
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void initExchangeAdapters() {
        registerAdapter(new BinanceExchangeAdapter(objectMapper, eventPublisher));
        registerAdapter(new CoinbaseExchangeAdapter(objectMapper, eventPublisher));
    }

    @Override
    public void registerAdapter(ExchangeAdapterPort adapter) {
        String exchangeName = adapter.getExchangeName().toUpperCase();
        adapters.put(exchangeName, adapter);
    }

    @Override
    public void registerPortfolioByAdapter(String exchangeName, UUID portfolioId) {
        exchangeName = exchangeName.toUpperCase();
        adapterByPortfolio.put(portfolioId, exchangeName);
    }

    @Override
    public Optional<ExchangeAdapterPort> findByName(String exchangeName) {
        return Optional.ofNullable(adapters.get(exchangeName.toUpperCase()));
    }

    @Override
    public Optional<ExchangeAdapterPort> findByPortfolioId(UUID portfolioId) {
        String exchangeName = adapterByPortfolio.get(portfolioId);
        return findByName(exchangeName);
    }

    @Override
    public boolean hasAdapter(String exchangeName) {
        return adapters.containsKey(exchangeName.toUpperCase());
    }

    @Override
    public Set<String> getAllExchangeNames() {
        return adapters.keySet();
    }

    @Override
    public Map<String, ExchangeAdapterPort> getAllAdapters() {
        return Map.copyOf(adapters);
    }

    @Override
    public int getAdapterCount() {
        return adapters.size();
    }
}
