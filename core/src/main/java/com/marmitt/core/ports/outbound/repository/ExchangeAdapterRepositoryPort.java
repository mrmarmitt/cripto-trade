package com.marmitt.core.ports.outbound.repository;

import com.marmitt.core.ports.outbound.exchange.rest.ExchangeAccountQueryPort;
import com.marmitt.core.ports.outbound.exchange.rest.ExchangeBootReadinessPort;
import com.marmitt.core.ports.outbound.exchange.rest.ExchangeOrderExecutionPort;
import com.marmitt.core.ports.outbound.exchange.rest.ExchangeOrderQueryPort;
import com.marmitt.core.ports.outbound.exchange.streaming.ExchangeStreamingPort;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface ExchangeAdapterRepositoryPort {

    void registerStreamingAdapter(ExchangeStreamingPort adapter);

    void registerOrderExecutionAdapter(String exchangeName, ExchangeOrderExecutionPort adapter);

    void registerOrderQueryAdapter(String exchangeName, ExchangeOrderQueryPort adapter);

    void registerAccountQueryAdapter(String exchangeName, ExchangeAccountQueryPort adapter);

    void registerBootReadinessAdapter(String exchangeName, ExchangeBootReadinessPort adapter);

    void registerPortfolioByAdapter(String exchangeName, UUID portfolioId);

    boolean hasAdapter(String exchangeName);

    Set<String> getAllExchangeNames();

    int getAdapterCount();

    Optional<ExchangeStreamingPort> findStreamingByName(String exchangeName);

    Optional<ExchangeOrderExecutionPort> findOrderExecutionByName(String exchangeName);

    Optional<ExchangeOrderQueryPort> findOrderQueryByName(String exchangeName);

    Optional<ExchangeAccountQueryPort> findAccountQueryByName(String exchangeName);

    Optional<ExchangeBootReadinessPort> findBootReadinessByName(String exchangeName);
}
