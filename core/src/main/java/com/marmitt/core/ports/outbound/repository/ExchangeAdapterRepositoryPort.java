package com.marmitt.core.ports.outbound.repository;

import com.marmitt.core.ports.outbound.exchange.ExchangeAdapterDescriptor;
import com.marmitt.core.ports.outbound.exchange.streaming.UserStreamSession;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface ExchangeAdapterRepositoryPort {

    Optional<ExchangeAdapterDescriptor> findAdapter(String exchangeName);

    boolean hasAdapter(String exchangeName);

    Set<String> getAllExchangeNames();

    void storeActiveSession(UUID connectionId, UserStreamSession session);

    Optional<UserStreamSession> findActiveSession(UUID connectionId);

    void removeActiveSession(UUID connectionId);

    void blockDispatch(String exchangeName);

    void unblockDispatch(String exchangeName);

    boolean isDispatchBlocked(String exchangeName);

    void registerPortfolioByAdapter(String exchangeName, UUID portfolioId);
}
