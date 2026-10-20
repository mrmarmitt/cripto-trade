package com.marmitt.core.domain.strategy;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Entrada do historico de uma estrategia remota (T54): o que mudou, por que e quando. Mudancas pedidas
 * pelo operador trazem o {@code reason} informado; conferencias trazem o resultado em {@code detail}.
 */
public record RemoteStrategyEvent(UUID id, UUID strategyRef, RemoteStrategyEventType type, String reason,
                                  String detail, Instant occurredAt) {

    public RemoteStrategyEvent {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(strategyRef, "strategyRef cannot be null");
        Objects.requireNonNull(type, "type cannot be null");
        Objects.requireNonNull(occurredAt, "occurredAt cannot be null");
    }

    public static RemoteStrategyEvent of(UUID strategyRef, RemoteStrategyEventType type, String reason,
                                         String detail, Instant occurredAt) {
        return new RemoteStrategyEvent(UUID.randomUUID(), strategyRef, type, reason, detail, occurredAt);
    }
}
