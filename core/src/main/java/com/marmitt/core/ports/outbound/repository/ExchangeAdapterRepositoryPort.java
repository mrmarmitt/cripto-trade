package com.marmitt.core.ports.outbound.repository;

import com.marmitt.core.ports.outbound.exchange.adapter.ExchangeAdapterPort;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface ExchangeAdapterRepositoryPort {

    void registerAdapter(ExchangeAdapterPort adapter);

    void registerPortfolioByAdapter(String exchangeName, UUID portfolioId);

    Optional<ExchangeAdapterPort> findByName(String exchangeName);

    boolean hasAdapter(String exchangeName);

    Set<String> getAllExchangeNames();

    Map<String, ExchangeAdapterPort> getAllAdapters();

    int getAdapterCount();
}
