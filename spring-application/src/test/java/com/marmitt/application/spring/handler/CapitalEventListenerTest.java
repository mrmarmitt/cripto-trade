package com.marmitt.application.spring.handler;

import com.marmitt.core.application.reaction.ExecutionConfirmedReaction;
import com.marmitt.core.application.reaction.MarginReleasedReaction;
import com.marmitt.core.domain.portfolio.DeadLetterEntry;
import com.marmitt.core.domain.runner.StrategyRunner;
import com.marmitt.core.domain.shared.Fee;
import com.marmitt.core.dto.capital.ExecutionConfirmation;
import com.marmitt.core.dto.capital.MarginRelease;
import com.marmitt.core.dto.events.ExecutionConfirmedEvent;
import com.marmitt.core.dto.events.MarginReleaseEvent;
import com.marmitt.core.enums.AccountingPolicyType;
import com.marmitt.core.enums.DlqReason;
import com.marmitt.core.enums.ExecutionPolicy;
import com.marmitt.core.enums.ReleaseReason;
import com.marmitt.core.enums.RunnerStatus;
import com.marmitt.core.ports.outbound.repository.DeadLetterEntryRepositoryPort;
import com.marmitt.core.ports.outbound.repository.StrategyRunnerRepositoryPort;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CapitalEventListenerTest {

    @Test
    void onExecutionConfirmedShouldDelegateToReaction() {
        ExecutionConfirmedReaction executionReaction = mock(ExecutionConfirmedReaction.class);
        MarginReleasedReaction marginReaction = mock(MarginReleasedReaction.class);
        StrategyRunnerRepositoryPort runnerRepository = mock(StrategyRunnerRepositoryPort.class);
        DeadLetterEntryRepositoryPort deadLetterRepository = mock(DeadLetterEntryRepositoryPort.class);
        CapitalEventListener listener = new CapitalEventListener(
                executionReaction,
                marginReaction,
                runnerRepository,
                deadLetterRepository
        );
        ExecutionConfirmedEvent event = executionConfirmedEvent(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

        listener.onExecutionConfirmed(event);

        verify(executionReaction).handle(event);
        verify(marginReaction, never()).handle(any(MarginReleaseEvent.class));
    }

    @Test
    void onMarginReleaseShouldDelegateToReaction() {
        ExecutionConfirmedReaction executionReaction = mock(ExecutionConfirmedReaction.class);
        MarginReleasedReaction marginReaction = mock(MarginReleasedReaction.class);
        StrategyRunnerRepositoryPort runnerRepository = mock(StrategyRunnerRepositoryPort.class);
        DeadLetterEntryRepositoryPort deadLetterRepository = mock(DeadLetterEntryRepositoryPort.class);
        CapitalEventListener listener = new CapitalEventListener(
                executionReaction,
                marginReaction,
                runnerRepository,
                deadLetterRepository
        );
        MarginReleaseEvent event = marginReleaseEvent(UUID.randomUUID(), UUID.randomUUID());

        listener.onMarginRelease(event);

        verify(marginReaction).handle(event);
        verify(executionReaction, never()).handle(any(ExecutionConfirmedEvent.class));
    }

    @Test
    void recoverExecutionConfirmedShouldPersistRetryExhaustedDlqWhenRunnerExists() {
        ExecutionConfirmedReaction executionReaction = mock(ExecutionConfirmedReaction.class);
        MarginReleasedReaction marginReaction = mock(MarginReleasedReaction.class);
        StrategyRunnerRepositoryPort runnerRepository = mock(StrategyRunnerRepositoryPort.class);
        DeadLetterEntryRepositoryPort deadLetterRepository = mock(DeadLetterEntryRepositoryPort.class);
        UUID portfolioId = UUID.randomUUID();
        UUID runnerId = UUID.randomUUID();
        when(runnerRepository.findById(runnerId)).thenReturn(Optional.of(activeRunner(portfolioId, runnerId)));
        CapitalEventListener listener = new CapitalEventListener(
                executionReaction,
                marginReaction,
                runnerRepository,
                deadLetterRepository
        );
        ExecutionConfirmedEvent event = executionConfirmedEvent(UUID.randomUUID(), runnerId, UUID.randomUUID());
        RuntimeException failure = new RuntimeException("temporary failure");

        listener.recoverExecutionConfirmed(failure, event);

        ArgumentCaptor<DeadLetterEntry> entryCaptor = ArgumentCaptor.forClass(DeadLetterEntry.class);
        verify(deadLetterRepository).save(entryCaptor.capture());
        DeadLetterEntry entry = entryCaptor.getValue();
        assertEquals(portfolioId, entry.getPortfolioId());
        assertEquals(runnerId, entry.getRunnerId());
        assertEquals(DlqReason.RETRY_EXHAUSTED, entry.getReason());
        assertTrue(entry.getRawPayload().contains("event=EXECUTION_CONFIRMED"));
        assertTrue(entry.getRawPayload().contains("transactionId=" + event.confirmation().transactionId()));
        assertTrue(entry.getRawPayload().contains("matchId=" + event.confirmation().matchId()));
        assertTrue(entry.getRawPayload().contains("failure=RuntimeException:temporary failure"));
    }

    @Test
    void recoverMarginReleaseShouldPersistRetryExhaustedDlqWhenRunnerExists() {
        ExecutionConfirmedReaction executionReaction = mock(ExecutionConfirmedReaction.class);
        MarginReleasedReaction marginReaction = mock(MarginReleasedReaction.class);
        StrategyRunnerRepositoryPort runnerRepository = mock(StrategyRunnerRepositoryPort.class);
        DeadLetterEntryRepositoryPort deadLetterRepository = mock(DeadLetterEntryRepositoryPort.class);
        UUID portfolioId = UUID.randomUUID();
        UUID runnerId = UUID.randomUUID();
        when(runnerRepository.findById(runnerId)).thenReturn(Optional.of(activeRunner(portfolioId, runnerId)));
        CapitalEventListener listener = new CapitalEventListener(
                executionReaction,
                marginReaction,
                runnerRepository,
                deadLetterRepository
        );
        MarginReleaseEvent event = marginReleaseEvent(UUID.randomUUID(), runnerId);
        RuntimeException failure = new RuntimeException("temporary failure");

        listener.recoverMarginRelease(failure, event);

        ArgumentCaptor<DeadLetterEntry> entryCaptor = ArgumentCaptor.forClass(DeadLetterEntry.class);
        verify(deadLetterRepository).save(entryCaptor.capture());
        DeadLetterEntry entry = entryCaptor.getValue();
        assertEquals(portfolioId, entry.getPortfolioId());
        assertEquals(runnerId, entry.getRunnerId());
        assertEquals(DlqReason.RETRY_EXHAUSTED, entry.getReason());
        assertTrue(entry.getRawPayload().contains("event=MARGIN_RELEASE"));
        assertTrue(entry.getRawPayload().contains("transactionId=" + event.release().transactionId()));
        assertTrue(entry.getRawPayload().contains("reason=" + event.release().reason()));
        assertTrue(entry.getRawPayload().contains("failure=RuntimeException:temporary failure"));
    }

    @Test
    void recoverExecutionConfirmedShouldNotPersistDlqWhenRunnerIsMissing() {
        ExecutionConfirmedReaction executionReaction = mock(ExecutionConfirmedReaction.class);
        MarginReleasedReaction marginReaction = mock(MarginReleasedReaction.class);
        StrategyRunnerRepositoryPort runnerRepository = mock(StrategyRunnerRepositoryPort.class);
        DeadLetterEntryRepositoryPort deadLetterRepository = mock(DeadLetterEntryRepositoryPort.class);
        UUID runnerId = UUID.randomUUID();
        when(runnerRepository.findById(runnerId)).thenReturn(Optional.empty());
        CapitalEventListener listener = new CapitalEventListener(
                executionReaction,
                marginReaction,
                runnerRepository,
                deadLetterRepository
        );
        ExecutionConfirmedEvent event = executionConfirmedEvent(UUID.randomUUID(), runnerId, UUID.randomUUID());

        listener.recoverExecutionConfirmed(new RuntimeException("temporary failure"), event);

        verify(deadLetterRepository, never()).save(any(DeadLetterEntry.class));
    }

    @Test
    void recoverMarginReleaseShouldSwallowPersistenceFailure() {
        ExecutionConfirmedReaction executionReaction = mock(ExecutionConfirmedReaction.class);
        MarginReleasedReaction marginReaction = mock(MarginReleasedReaction.class);
        StrategyRunnerRepositoryPort runnerRepository = mock(StrategyRunnerRepositoryPort.class);
        DeadLetterEntryRepositoryPort deadLetterRepository = mock(DeadLetterEntryRepositoryPort.class);
        UUID portfolioId = UUID.randomUUID();
        UUID runnerId = UUID.randomUUID();
        when(runnerRepository.findById(runnerId)).thenReturn(Optional.of(activeRunner(portfolioId, runnerId)));
        doThrow(new RuntimeException("db down")).when(deadLetterRepository).save(any(DeadLetterEntry.class));
        CapitalEventListener listener = new CapitalEventListener(
                executionReaction,
                marginReaction,
                runnerRepository,
                deadLetterRepository
        );
        MarginReleaseEvent event = marginReleaseEvent(UUID.randomUUID(), runnerId);

        assertDoesNotThrow(() ->
                listener.recoverMarginRelease(new RuntimeException("temporary failure"), event));

        verify(deadLetterRepository).save(any(DeadLetterEntry.class));
    }

    private static ExecutionConfirmedEvent executionConfirmedEvent(UUID transactionId, UUID runnerId, UUID matchId) {
        return new ExecutionConfirmedEvent(new ExecutionConfirmation(
                transactionId,
                runnerId,
                matchId,
                new BigDecimal("0.01000000"),
                new BigDecimal("65000.00"),
                Fee.zero("USDT"),
                new BigDecimal("650.00"),
                new BigDecimal("15.00"),
                true
        ));
    }

    private static MarginReleaseEvent marginReleaseEvent(UUID transactionId, UUID runnerId) {
        return new MarginReleaseEvent(MarginRelease.fullRelease(
                transactionId,
                runnerId,
                new BigDecimal("650.00"),
                ReleaseReason.EXPIRED
        ));
    }

    private static StrategyRunner activeRunner(UUID portfolioId, UUID runnerId) {
        return new StrategyRunner(
                runnerId,
                portfolioId,
                "x1",
                UUID.randomUUID(),
                "SimpleMovingAverage",
                "BTCUSDT",
                "MOCK",
                Set.of("MOCK"),
                RunnerStatus.ACTIVE,
                ExecutionPolicy.SINGLE,
                AccountingPolicyType.FIFO,
                new BigDecimal("0.25"),
                1,
                1,
                null,
                false,
                Instant.now(),
                null,
                null,
                0L
        );
    }
}
