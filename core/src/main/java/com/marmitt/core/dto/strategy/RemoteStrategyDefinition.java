package com.marmitt.core.dto.strategy;

import java.util.Objects;
import java.util.UUID;

/**
 * Entrada do catálogo de estratégias remotas (T41): a versão que o ctrade espera encontrar no
 * serviço. Onde o serviço mora (URL, credencial) é conhecimento do adapter, não do core.
 */
public record RemoteStrategyDefinition(UUID strategyRef, String name, String version) {

    public RemoteStrategyDefinition {
        Objects.requireNonNull(strategyRef, "strategyRef cannot be null");
        Objects.requireNonNull(name, "name cannot be null");
        Objects.requireNonNull(version, "version cannot be null");
    }
}
