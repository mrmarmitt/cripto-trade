package com.marmitt.application.spring.repository;

import com.marmitt.core.ports.outbound.exchange.ExchangeAdapterDescriptor;
import com.marmitt.core.ports.outbound.exchange.streaming.UserStreamSession;
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

    private final Map<String, ExchangeAdapterDescriptor> adapters = new ConcurrentHashMap<>();
    private final Map<UUID, UserStreamSession> activeSessions = new ConcurrentHashMap<>();
    private final Set<String> blockedDispatches = ConcurrentHashMap.newKeySet();
    private final Map<UUID, String> adapterByPortfolio = new ConcurrentHashMap<>();

    public InMemoryExchangeAdapterRepository(List<ExchangeAdapterDescriptor> descriptors) {
        descriptors.forEach(d -> {
            String name = d.exchangeName().toUpperCase();
            adapters.put(name, d);
            log.info("Exchange adapter registered - exchange={} userStream={} orderQuery={} accountQuery={} bootReadiness={}",
                    name,
                    d.hasUserStream(),
                    d.hasOrderQuery(),
                    d.hasAccountQuery(),
                    d.hasBootReadiness());
        });
    }

    @Override
    public Optional<ExchangeAdapterDescriptor> findAdapter(String exchangeName) {
        return Optional.ofNullable(adapters.get(normalize(exchangeName)));
    }

    @Override
    public boolean hasAdapter(String exchangeName) {
        return adapters.containsKey(normalize(exchangeName));
    }

    @Override
    public Set<String> getAllExchangeNames() {
        return adapters.keySet();
    }

    @Override
    public void storeActiveSession(UUID connectionId, UserStreamSession session) {
        activeSessions.put(connectionId, session);
    }

    @Override
    public Optional<UserStreamSession> findActiveSession(UUID connectionId) {
        return Optional.ofNullable(activeSessions.get(connectionId));
    }

    @Override
    public void removeActiveSession(UUID connectionId) {
        activeSessions.remove(connectionId);
    }

    @Override
    public void blockDispatch(String exchangeName) {
        blockedDispatches.add(normalize(exchangeName));
        log.warn("Dispatch blocked for exchange={}", normalize(exchangeName));
    }

    @Override
    public void unblockDispatch(String exchangeName) {
        blockedDispatches.remove(normalize(exchangeName));
        log.info("Dispatch unblocked for exchange={}", normalize(exchangeName));
    }

    @Override
    public boolean isDispatchBlocked(String exchangeName) {
        return blockedDispatches.contains(normalize(exchangeName));
    }

    @Override
    public void registerPortfolioByAdapter(String exchangeName, UUID portfolioId) {
        adapterByPortfolio.put(portfolioId, normalize(exchangeName));
    }

    private static String normalize(String exchangeName) {
        return exchangeName.toUpperCase();
    }
}
