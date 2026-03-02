package com.marmitt.application.spring.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marmitt.application.spring.config.exchange.BinanceExchangeAdapter;
import com.marmitt.application.spring.config.exchange.CoinbaseExchangeAdapter;
import com.marmitt.application.spring.config.exchange.MockExchangeAdapter;
import com.marmitt.core.ports.outbound.events.EventPublisherPort;
import com.marmitt.core.ports.outbound.exchange.rest.ExchangeAccountQueryPort;
import com.marmitt.core.ports.outbound.exchange.rest.ExchangeOrderExecutionPort;
import com.marmitt.core.ports.outbound.exchange.rest.ExchangeOrderQueryPort;
import com.marmitt.core.ports.outbound.exchange.streaming.ExchangeStreamingPort;
import com.marmitt.core.ports.outbound.repository.ExchangeAdapterRepositoryPort;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Repository
public class InMemoryExchangeAdapterRepository implements ExchangeAdapterRepositoryPort {

    private final Map<String, ExchangeStreamingPort> streamingAdapters = new ConcurrentHashMap<>();
    private final Map<String, ExchangeOrderExecutionPort> orderExecutionAdapters = new ConcurrentHashMap<>();
    private final Map<String, ExchangeOrderQueryPort> orderQueryAdapters = new ConcurrentHashMap<>();
    private final Map<String, ExchangeAccountQueryPort> accountQueryAdapters = new ConcurrentHashMap<>();
    private final Map<UUID, String> adapterByPortfolio = new ConcurrentHashMap<>();

    private final EventPublisherPort eventPublisher;
    private final ObjectMapper objectMapper;

    public InMemoryExchangeAdapterRepository(EventPublisherPort eventPublisher, ObjectMapper objectMapper) {
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void initExchangeAdapters() {
        registerAllCapabilities(new BinanceExchangeAdapter(objectMapper, eventPublisher));
        registerAllCapabilities(new CoinbaseExchangeAdapter(objectMapper, eventPublisher));
        registerAllCapabilities(new MockExchangeAdapter(objectMapper, eventPublisher));
    }

    @Override
    public void registerStreamingAdapter(ExchangeStreamingPort adapter) {
        String exchangeName = normalize(adapter.getExchangeName());
        streamingAdapters.put(exchangeName, adapter);
    }

    @Override
    public void registerOrderExecutionAdapter(String exchangeName, ExchangeOrderExecutionPort adapter) {
        orderExecutionAdapters.put(normalize(exchangeName), adapter);
    }

    @Override
    public void registerOrderQueryAdapter(String exchangeName, ExchangeOrderQueryPort adapter) {
        orderQueryAdapters.put(normalize(exchangeName), adapter);
    }

    @Override
    public void registerAccountQueryAdapter(String exchangeName, ExchangeAccountQueryPort adapter) {
        accountQueryAdapters.put(normalize(exchangeName), adapter);
    }

    @Override
    public void registerPortfolioByAdapter(String exchangeName, UUID portfolioId) {
        adapterByPortfolio.put(portfolioId, normalize(exchangeName));
    }

    @Override
    public boolean hasAdapter(String exchangeName) {
        return streamingAdapters.containsKey(normalize(exchangeName));
    }

    @Override
    public Set<String> getAllExchangeNames() {
        return streamingAdapters.keySet();
    }

    @Override
    public int getAdapterCount() {
        return streamingAdapters.size();
    }

    @Override
    public Optional<ExchangeStreamingPort> findStreamingByName(String exchangeName) {
        return Optional.ofNullable(streamingAdapters.get(normalize(exchangeName)));
    }

    @Override
    public Optional<ExchangeOrderExecutionPort> findOrderExecutionByName(String exchangeName) {
        return Optional.ofNullable(orderExecutionAdapters.get(normalize(exchangeName)));
    }

    @Override
    public Optional<ExchangeOrderQueryPort> findOrderQueryByName(String exchangeName) {
        return Optional.ofNullable(orderQueryAdapters.get(normalize(exchangeName)));
    }

    @Override
    public Optional<ExchangeAccountQueryPort> findAccountQueryByName(String exchangeName) {
        return Optional.ofNullable(accountQueryAdapters.get(normalize(exchangeName)));
    }

    private void registerAllCapabilities(ExchangeStreamingPort streamingAdapter) {
        String exchangeName = normalize(streamingAdapter.getExchangeName());
        registerStreamingAdapter(streamingAdapter);

        if (streamingAdapter instanceof ExchangeOrderExecutionPort orderExecutionPort) {
            registerOrderExecutionAdapter(exchangeName, orderExecutionPort);
        }
        if (streamingAdapter instanceof ExchangeOrderQueryPort orderQueryPort) {
            registerOrderQueryAdapter(exchangeName, orderQueryPort);
        }
        if (streamingAdapter instanceof ExchangeAccountQueryPort accountQueryPort) {
            registerAccountQueryAdapter(exchangeName, accountQueryPort);
        }

        log.info("Exchange capabilities registered - exchange={} streaming={} orderExec={} orderQuery={} accountQuery={}",
                exchangeName,
                true,
                orderExecutionAdapters.containsKey(exchangeName),
                orderQueryAdapters.containsKey(exchangeName),
                accountQueryAdapters.containsKey(exchangeName));
    }

    private static String normalize(String exchangeName) {
        return exchangeName.toUpperCase();
    }
}

