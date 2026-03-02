package com.marmitt.core.ports.outbound.repository;

import com.marmitt.core.ports.outbound.exchange.adapter.ExchangeAdapterPort;
import com.marmitt.core.ports.outbound.exchange.rest.ExchangeAccountQueryPort;
import com.marmitt.core.ports.outbound.exchange.rest.ExchangeOrderExecutionPort;
import com.marmitt.core.ports.outbound.exchange.rest.ExchangeOrderQueryPort;
import com.marmitt.core.ports.outbound.exchange.streaming.ExchangeStreamingPort;

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

    /**
     * Resolve capacidade de streaming para a exchange.
     * Compatibilidade: todas as implementacoes legadas de {@link ExchangeAdapterPort}
     * ja expõem streaming.
     */
    default Optional<ExchangeStreamingPort> findStreamingByName(String exchangeName) {
        return findByName(exchangeName).map(adapter -> (ExchangeStreamingPort) adapter);
    }

    /**
     * Resolve capacidade REST de comando (submit/cancel).
     * Retorna vazio enquanto a exchange ainda nao implementa essa capability.
     */
    default Optional<ExchangeOrderExecutionPort> findOrderExecutionByName(String exchangeName) {
        return findByName(exchangeName)
                .filter(ExchangeOrderExecutionPort.class::isInstance)
                .map(ExchangeOrderExecutionPort.class::cast);
    }

    /**
     * Resolve capacidade REST de consulta de ordens.
     */
    default Optional<ExchangeOrderQueryPort> findOrderQueryByName(String exchangeName) {
        return findByName(exchangeName)
                .filter(ExchangeOrderQueryPort.class::isInstance)
                .map(ExchangeOrderQueryPort.class::cast);
    }

    /**
     * Resolve capacidade REST de consulta de conta/saldo.
     */
    default Optional<ExchangeAccountQueryPort> findAccountQueryByName(String exchangeName) {
        return findByName(exchangeName)
                .filter(ExchangeAccountQueryPort.class::isInstance)
                .map(ExchangeAccountQueryPort.class::cast);
    }
}
