package com.marmitt.core.ports.outbound.repository;

import com.marmitt.core.domain.strategy.RemoteStrategy;
import com.marmitt.core.domain.strategy.RemoteStrategyEvent;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistencia do catalogo de estrategias remotas e do historico de cada uma (T54).
 */
public interface RemoteStrategyCatalogRepositoryPort {

    Optional<RemoteStrategy> findByRef(UUID strategyRef);

    List<RemoteStrategy> findAll();

    /**
     * Grava o estado da estrategia (insere ou atualiza) e acrescenta os eventos, atomicamente.
     */
    void save(RemoteStrategy strategy, List<RemoteStrategyEvent> events);

    /**
     * Historico da estrategia, do mais antigo para o mais recente.
     */
    List<RemoteStrategyEvent> findEvents(UUID strategyRef);
}
