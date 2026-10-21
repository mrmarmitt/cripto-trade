package com.marmitt.core.application.usecase.operations;

import com.marmitt.core.dto.operations.EventTimeline;
import com.marmitt.core.dto.operations.TimelineEvent;
import com.marmitt.core.enums.TimelineEventType;
import com.marmitt.core.ports.outbound.repository.EventTimelineRepositoryPort;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T59: intervalo padrao, limite e paginacao da linha do tempo; as fontes sao do adapter.
 */
class QueryEventTimelineUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-10-21T18:00:00Z");

    private final EventTimelineRepositoryPort repository = mock(EventTimelineRepositoryPort.class);
    private final QueryEventTimelineUseCase useCase =
            new QueryEventTimelineUseCase(repository, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void defaultsToTheLast24HoursAndAsksOneMoreThanTheLimit() {
        UUID portfolioId = UUID.randomUUID();
        when(repository.find(any(), any(), any(), anyInt())).thenReturn(List.of(event(NOW.minusSeconds(60))));

        EventTimeline timeline = useCase.find(null, null, portfolioId, null);

        verify(repository).find(NOW.minus(Duration.ofHours(24)), NOW, portfolioId,
                QueryEventTimelineUseCase.DEFAULT_LIMIT + 1);
        assertEquals(NOW.minus(Duration.ofHours(24)), timeline.since());
        assertEquals(NOW, timeline.until());
        assertEquals(1, timeline.events().size());
        assertFalse(timeline.truncated());
        assertNull(timeline.nextSince());
    }

    @Test
    void truncatedPagePointsToTheInstantOfItsLastEvent() {
        Instant since = NOW.minus(Duration.ofHours(1));
        List<TimelineEvent> found = IntStream.range(0, 4).mapToObj(i -> event(since.plusSeconds(i))).toList();
        when(repository.find(since, NOW, null, 4)).thenReturn(found);

        EventTimeline timeline = useCase.find(since, null, null, 3);

        assertEquals(3, timeline.events().size());
        assertTrue(timeline.truncated());
        assertEquals(since.plusSeconds(2), timeline.nextSince());
    }

    @Test
    void limitIsCappedAndRangeMustMoveForward() {
        when(repository.find(any(), any(), any(), anyInt())).thenReturn(List.of());

        useCase.find(null, null, null, 50_000);
        verify(repository).find(any(), eq(NOW), any(), eq(QueryEventTimelineUseCase.MAX_LIMIT + 1));

        assertThrows(IllegalArgumentException.class, () -> useCase.find(NOW, NOW, null, null));
        assertThrows(IllegalArgumentException.class, () -> useCase.find(NOW.plusSeconds(1), NOW, null, null));
    }

    private static TimelineEvent event(Instant at) {
        return new TimelineEvent(UUID.randomUUID().toString(), TimelineEventType.RUNNER_STATUS_CHANGED, at, null,
                null, null, null, "MANUAL", null, "ACTIVE", "HALTED", "test", null);
    }
}
