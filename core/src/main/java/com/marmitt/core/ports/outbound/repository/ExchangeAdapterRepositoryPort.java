package com.marmitt.core.ports.outbound.repository;

import com.marmitt.core.ports.outbound.ExchangeAdapterPort;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

public interface ExchangeAdapterRepositoryPort {

    void registerAdapter(ExchangeAdapterPort adapter);

    ExchangeAdapterPort getAdapter(String exchangeName);

    Optional<ExchangeAdapterPort> findAdapter(String exchangeName);

    boolean hasAdapter(String exchangeName);

    Set<String> getAllExchangeNames();

    Map<String, ExchangeAdapterPort> getAllAdapters();

    int getAdapterCount();
}
