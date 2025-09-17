package com.marmitt.repository;

import com.marmitt.config.exchange.BinanceExchangeAdapter;
import com.marmitt.config.exchange.CoinbaseExchangeAdapter;
import com.marmitt.core.ports.outbound.ExchangeAdapterPort;
import jakarta.annotation.PostConstruct;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryExchangeAdapterRepository {

    private final Map<String, ExchangeAdapterPort> adapters = new ConcurrentHashMap<>();

    private final ApplicationEventPublisher eventPublisher;

    public InMemoryExchangeAdapterRepository(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @PostConstruct
    public void initExchangeAdapters() {
        registerAdapter(new BinanceExchangeAdapter(eventPublisher));
        registerAdapter(new CoinbaseExchangeAdapter(eventPublisher));
    }

    /**
     * Registra um adapter para uma exchange específica.
     * Usado durante a inicialização do Spring.
     */
    public void registerAdapter(ExchangeAdapterPort adapter) {
        String exchangeName = adapter.getExchangeName().toUpperCase();
        adapters.put(exchangeName, adapter);
    }

    /**
     * Retorna o adapter para uma exchange específica.
     * @param exchangeName Nome da exchange (case-insensitive)
     * @return ExchangeAdapter ou null se não encontrado
     */
    public ExchangeAdapterPort getAdapter(String exchangeName) {
        return adapters.get(exchangeName.toUpperCase());
    }

    /**
     * Retorna o adapter para uma exchange específica de forma segura.
     * @param exchangeName Nome da exchange (case-insensitive)
     * @return Optional contendo o adapter se encontrado
     */
    public Optional<ExchangeAdapterPort> findAdapter(String exchangeName) {
        return Optional.ofNullable(getAdapter(exchangeName));
    }

    /**
     * Verifica se existe um adapter registrado para a exchange.
     */
    public boolean hasAdapter(String exchangeName) {
        return adapters.containsKey(exchangeName.toUpperCase());
    }

    /**
     * Retorna todos os nomes de exchanges registradas.
     */
    public Set<String> getAllExchangeNames() {
        return adapters.keySet();
    }

    /**
     * Retorna uma cópia imutável de todos os adapters registrados.
     */
    public Map<String, ExchangeAdapterPort> getAllAdapters() {
        return Map.copyOf(adapters);
    }

    /**
     * Retorna o número de adapters registrados.
     */
    public int getAdapterCount() {
        return adapters.size();
    }
}
