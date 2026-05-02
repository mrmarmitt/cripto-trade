package com.marmitt.application.spring.repository;

import com.marmitt.core.ports.outbound.exchange.rest.ExchangeAccountQueryPort;
import com.marmitt.core.ports.outbound.exchange.rest.ExchangeBootReadinessPort;
import com.marmitt.core.ports.outbound.exchange.rest.ExchangeOrderExecutionPort;
import com.marmitt.core.ports.outbound.exchange.rest.ExchangeOrderQueryPort;
import com.marmitt.core.ports.outbound.exchange.streaming.ExchangeStreamingPort;
import com.marmitt.core.ports.outbound.exchange.streaming.ExchangeUserStreamPort;
import com.marmitt.core.ports.outbound.repository.ExchangeAdapterRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryExchangeAdapterRepository implements ExchangeAdapterRepositoryPort {

    private static final Logger log = LoggerFactory.getLogger(InMemoryExchangeAdapterRepository.class);

    private final Map<String, ExchangeStreamingPort> streamingAdapters = new ConcurrentHashMap<>();
    private final Map<String, ExchangeUserStreamPort> userStreamAdapters = new ConcurrentHashMap<>();
    private final Map<String, ExchangeOrderExecutionPort> orderExecutionAdapters = new ConcurrentHashMap<>();
    private final Map<String, ExchangeOrderQueryPort> orderQueryAdapters = new ConcurrentHashMap<>();
    private final Map<String, ExchangeAccountQueryPort> accountQueryAdapters = new ConcurrentHashMap<>();
    private final Map<String, ExchangeBootReadinessPort> bootReadinessAdapters = new ConcurrentHashMap<>();
    private final Map<UUID, String> adapterByPortfolio = new ConcurrentHashMap<>();

    public InMemoryExchangeAdapterRepository(List<ExchangeStreamingPort> adapters,
                                             List<ExchangeUserStreamPort> userStreamAdapters) {
        adapters.forEach(this::registerAllCapabilities);
        userStreamAdapters.forEach(this::registerUserStreamAdapter);
    }

    @Override
    public void registerStreamingAdapter(ExchangeStreamingPort adapter) {
        String exchangeName = normalize(adapter.getExchangeName());
        streamingAdapters.put(exchangeName, adapter);
    }

    @Override
    public void registerUserStreamAdapter(ExchangeUserStreamPort adapter) {
        userStreamAdapters.put(normalize(adapter.getExchangeName()), adapter);
        log.info("User stream adapter registered - exchange={}", normalize(adapter.getExchangeName()));
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
    public void registerBootReadinessAdapter(String exchangeName, ExchangeBootReadinessPort adapter) {
        bootReadinessAdapters.put(normalize(exchangeName), adapter);
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

    @Override
    public Optional<ExchangeBootReadinessPort> findBootReadinessByName(String exchangeName) {
        return Optional.ofNullable(bootReadinessAdapters.get(normalize(exchangeName)));
    }

    @Override
    public Optional<ExchangeUserStreamPort> findUserStreamByName(String exchangeName) {
        return Optional.ofNullable(userStreamAdapters.get(normalize(exchangeName)));
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
        if (streamingAdapter instanceof ExchangeBootReadinessPort bootReadinessPort) {
            registerBootReadinessAdapter(exchangeName, bootReadinessPort);
        }

        log.info("Exchange capabilities registered - exchange={} streaming={} orderExec={} orderQuery={} accountQuery={} bootReadiness={}",
                exchangeName,
                true,
                orderExecutionAdapters.containsKey(exchangeName),
                orderQueryAdapters.containsKey(exchangeName),
                accountQueryAdapters.containsKey(exchangeName),
                bootReadinessAdapters.containsKey(exchangeName));
    }

    private static String normalize(String exchangeName) {
        return exchangeName.toUpperCase();
    }
}
