package com.marmitt.core.ports.inbound.strategy;

import com.marmitt.core.dto.strategy.RemoteStrategyDefinition;

import java.util.List;
import java.util.UUID;

/**
 * Registra as estratégias remotas do catálogo que ainda não estão registradas (T41). Idempotente:
 * chamado no boot e periodicamente, para pegar serviços que subiram depois do ctrade.
 */
public interface RegisterRemoteStrategiesPort {

    /**
     * @return {@code strategyRef} das entradas registradas nesta chamada
     */
    List<UUID> registerPending(List<RemoteStrategyDefinition> catalog);
}
