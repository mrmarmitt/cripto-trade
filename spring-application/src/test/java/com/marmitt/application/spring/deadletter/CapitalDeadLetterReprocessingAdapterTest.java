package com.marmitt.application.spring.deadletter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marmitt.core.application.reaction.ExecutionConfirmedReaction;
import com.marmitt.core.application.reaction.MarginReleasedReaction;
import com.marmitt.core.domain.portfolio.DeadLetterEntry;
import com.marmitt.core.domain.shared.Fee;
import com.marmitt.core.dto.capital.ExecutionConfirmation;
import com.marmitt.core.dto.capital.MarginRelease;
import com.marmitt.core.dto.events.ExecutionConfirmedEvent;
import com.marmitt.core.dto.events.MarginReleaseEvent;
import com.marmitt.core.enums.DlqReason;
import com.marmitt.core.enums.ReleaseReason;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class CapitalDeadLetterReprocessingAdapterTest {

    private final ExecutionConfirmedReaction executionReaction = mock(ExecutionConfirmedReaction.class);
    private final MarginReleasedReaction marginReaction = mock(MarginReleasedReaction.class);
    private final CapitalDeadLetterPayloadCodec codec = new CapitalDeadLetterPayloadCodec(new ObjectMapper());

    @Test
    void supportsShouldReturnTrueForRetryExhaustedExecutionPayload() {
        CapitalDeadLetterReprocessingAdapter adapter = new CapitalDeadLetterReprocessingAdapter(
                executionReaction,
                marginReaction,
                codec
        );
        DeadLetterEntry entry = new DeadLetterEntry(
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                null,
                codec.encodeExecutionConfirmed(executionEvent(), new RuntimeException("temporary failure")),
                DlqReason.RETRY_EXHAUSTED
        );

        assertTrue(adapter.supports(entry));
    }

    @Test
    void supportsShouldReturnFalseForUnsupportedReason() {
        CapitalDeadLetterReprocessingAdapter adapter = new CapitalDeadLetterReprocessingAdapter(
                executionReaction,
                marginReaction,
                codec
        );
        DeadLetterEntry entry = new DeadLetterEntry(
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                null,
                codec.encodeExecutionConfirmed(executionEvent(), new RuntimeException("temporary failure")),
                DlqReason.RECONCILIATION_CONFLICT
        );

        assertFalse(adapter.supports(entry));
    }

    @Test
    void reprocessShouldDispatchExecutionPayloadToExecutionReaction() {
        CapitalDeadLetterReprocessingAdapter adapter = new CapitalDeadLetterReprocessingAdapter(
                executionReaction,
                marginReaction,
                codec
        );
        ExecutionConfirmedEvent original = executionEvent();
        DeadLetterEntry entry = new DeadLetterEntry(
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                null,
                codec.encodeExecutionConfirmed(original, new RuntimeException("temporary failure")),
                DlqReason.RETRY_EXHAUSTED
        );

        adapter.reprocess(entry);

        ArgumentCaptor<ExecutionConfirmedEvent> captor = ArgumentCaptor.forClass(ExecutionConfirmedEvent.class);
        verify(executionReaction).handle(captor.capture());
        verifyNoInteractions(marginReaction);
        ExecutionConfirmedEvent replayed = captor.getValue();
        assertTrue(replayed.confirmation().transactionId().equals(original.confirmation().transactionId()));
        assertTrue(replayed.confirmation().matchId().equals(original.confirmation().matchId()));
        assertTrue(replayed.confirmation().totalCost().compareTo(original.confirmation().totalCost()) == 0);
    }

    @Test
    void reprocessShouldDispatchMarginPayloadToMarginReaction() {
        CapitalDeadLetterReprocessingAdapter adapter = new CapitalDeadLetterReprocessingAdapter(
                executionReaction,
                marginReaction,
                codec
        );
        MarginReleaseEvent original = marginEvent();
        DeadLetterEntry entry = new DeadLetterEntry(
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                null,
                codec.encodeMarginRelease(original, new RuntimeException("temporary failure")),
                DlqReason.RETRY_EXHAUSTED
        );

        adapter.reprocess(entry);

        ArgumentCaptor<MarginReleaseEvent> captor = ArgumentCaptor.forClass(MarginReleaseEvent.class);
        verify(marginReaction).handle(captor.capture());
        verifyNoInteractions(executionReaction);
        MarginReleaseEvent replayed = captor.getValue();
        assertTrue(replayed.release().transactionId().equals(original.release().transactionId()));
        assertTrue(replayed.release().releaseAmount().compareTo(original.release().releaseAmount()) == 0);
        assertTrue(replayed.release().executedAmount().compareTo(original.release().executedAmount()) == 0);
    }

    private static ExecutionConfirmedEvent executionEvent() {
        return new ExecutionConfirmedEvent(new ExecutionConfirmation(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                new BigDecimal("0.01000000"),
                new BigDecimal("65000.00"),
                Fee.zero("USDT"),
                new BigDecimal("650.00"),
                new BigDecimal("15.00"),
                true
        ));
    }

    private static MarginReleaseEvent marginEvent() {
        return new MarginReleaseEvent(new MarginRelease(
                UUID.randomUUID(),
                UUID.randomUUID(),
                new BigDecimal("650.00"),
                ReleaseReason.CANCELED,
                new BigDecimal("120.00")
        ));
    }
}
