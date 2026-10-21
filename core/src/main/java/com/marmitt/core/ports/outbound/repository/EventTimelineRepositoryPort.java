package com.marmitt.core.ports.outbound.repository;

import com.marmitt.core.dto.operations.TimelineEvent;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Leitura dos eventos nas tabelas de origem (T59), unidos em ordem cronologica. Somente leitura.
 */
public interface EventTimelineRepositoryPort {

    /**
     * Eventos com {@code since <= occurredAt < until}, por {@code occurredAt} e depois {@code id}, ate
     * {@code limit}. Com {@code portfolioId}, so os tipos ligados a portfolio.
     */
    List<TimelineEvent> find(Instant since, Instant until, UUID portfolioId, int limit);
}
