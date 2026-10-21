package com.marmitt.core.application.usecase.operations;

import com.marmitt.core.dto.operations.EventTimeline;
import com.marmitt.core.dto.operations.TimelineEvent;
import com.marmitt.core.ports.inbound.operations.QueryEventTimelinePort;
import com.marmitt.core.ports.outbound.repository.EventTimelineRepositoryPort;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Linha do tempo de eventos (T59). Le das tabelas que ja registram cada evento; pede um evento a mais que
 * o limite para saber se a pagina foi truncada.
 */
public class QueryEventTimelineUseCase implements QueryEventTimelinePort {

    static final Duration DEFAULT_WINDOW = Duration.ofHours(24);
    static final int DEFAULT_LIMIT = 200;
    static final int MAX_LIMIT = 1000;

    private final EventTimelineRepositoryPort repository;
    private final Clock clock;

    public QueryEventTimelineUseCase(EventTimelineRepositoryPort repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    public EventTimeline find(Instant since, Instant until, UUID portfolioId, Integer limit) {
        Instant to = until != null ? until : clock.instant();
        Instant from = since != null ? since : to.minus(DEFAULT_WINDOW);
        if (!from.isBefore(to)) {
            throw new IllegalArgumentException("since must be before until");
        }
        int max = limit != null ? Math.max(1, Math.min(limit, MAX_LIMIT)) : DEFAULT_LIMIT;

        List<TimelineEvent> found = repository.find(from, to, portfolioId, max + 1);
        boolean truncated = found.size() > max;
        List<TimelineEvent> events = truncated ? found.subList(0, max) : found;
        Instant nextSince = truncated ? events.get(events.size() - 1).occurredAt() : null;
        return new EventTimeline(from, to, List.copyOf(events), truncated, nextSince);
    }
}
