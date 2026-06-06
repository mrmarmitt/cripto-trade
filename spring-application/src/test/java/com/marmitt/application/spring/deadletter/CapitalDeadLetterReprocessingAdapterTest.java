package com.marmitt.application.spring.deadletter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marmitt.core.application.reaction.ExecutionConfirmedReaction;
import com.marmitt.core.application.reaction.MarginReleasedReaction;
import com.marmitt.core.domain.portfolio.GlobalBalance;
import com.marmitt.core.domain.portfolio.DeadLetterEntry;
import com.marmitt.core.domain.runner.StrategyRunner;
import com.marmitt.core.domain.shared.Fee;
import com.marmitt.core.dto.capital.ExecutionConfirmation;
import com.marmitt.core.dto.capital.MarginRelease;
import com.marmitt.core.dto.portfolio.DeadLetterReprocessingResult;
import com.marmitt.core.enums.AccountingPolicyType;
import com.marmitt.core.dto.events.ExecutionConfirmedEvent;
import com.marmitt.core.dto.events.MarginReleaseEvent;
import com.marmitt.core.enums.DlqReason;
import com.marmitt.core.enums.ExecutionPolicy;
import com.marmitt.core.enums.ReleaseReason;
import com.marmitt.core.enums.RunnerStatus;
import com.marmitt.core.ports.outbound.repository.GlobalBalanceRepositoryPort;
import com.marmitt.core.ports.outbound.repository.StrategyRunnerRepositoryPort;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CapitalDeadLetterReprocessingAdapterTest {

    private final ExecutionConfirmedReaction executionReaction = mock(ExecutionConfirmedReaction.class);
    private final MarginReleasedReaction marginReaction = mock(MarginReleasedReaction.class);
    private final StrategyRunnerRepositoryPort runnerRepository = mock(StrategyRunnerRepositoryPort.class);
    private final GlobalBalanceRepositoryPort globalBalanceRepository = mock(GlobalBalanceRepositoryPort.class);
    private final CapitalDeadLetterPayloadCodec codec = new CapitalDeadLetterPayloadCodec(new ObjectMapper());

    @Test
    void supportsShouldReturnTrueForRetryExhaustedExecutionPayload() {
        CapitalDeadLetterReprocessingAdapter adapter = new CapitalDeadLetterReprocessingAdapter(
                executionReaction,
                marginReaction,
                codec,
                runnerRepository,
                globalBalanceRepository
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
                codec,
                runnerRepository,
                globalBalanceRepository
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
                codec,
                runnerRepository,
                globalBalanceRepository
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

        DeadLetterReprocessingResult result = adapter.reprocess(entry);

        ArgumentCaptor<ExecutionConfirmedEvent> captor = ArgumentCaptor.forClass(ExecutionConfirmedEvent.class);
        verify(executionReaction).handle(captor.capture());
        verifyNoInteractions(marginReaction);
        assertTrue(result.applied());
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
                codec,
                runnerRepository,
                globalBalanceRepository
        );
        MarginReleaseEvent original = marginEvent();
        UUID portfolioId = UUID.randomUUID();
        when(runnerRepository.findById(original.release().runnerId()))
                .thenReturn(Optional.of(activeRunner(portfolioId, original.release().runnerId())));
        when(globalBalanceRepository.findByPortfolioId(portfolioId))
                .thenReturn(Optional.of(balanceWithReserved(portfolioId, new BigDecimal("1000.00"))));
        DeadLetterEntry entry = new DeadLetterEntry(
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                null,
                codec.encodeMarginRelease(original, new RuntimeException("temporary failure")),
                DlqReason.RETRY_EXHAUSTED
        );

        DeadLetterReprocessingResult result = adapter.reprocess(entry);

        ArgumentCaptor<MarginReleaseEvent> captor = ArgumentCaptor.forClass(MarginReleaseEvent.class);
        verify(marginReaction).handle(captor.capture());
        verifyNoInteractions(executionReaction);
        assertTrue(result.applied());
        MarginReleaseEvent replayed = captor.getValue();
        assertTrue(replayed.release().transactionId().equals(original.release().transactionId()));
        assertTrue(replayed.release().releaseAmount().compareTo(original.release().releaseAmount()) == 0);
        assertTrue(replayed.release().executedAmount().compareTo(original.release().executedAmount()) == 0);
    }

    @Test
    void supportsShouldReturnFalseForMalformedRecognizedPayload() {
        CapitalDeadLetterReprocessingAdapter adapter = new CapitalDeadLetterReprocessingAdapter(
                executionReaction,
                marginReaction,
                codec,
                runnerRepository,
                globalBalanceRepository
        );
        DeadLetterEntry entry = new DeadLetterEntry(
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                null,
                "{\"eventType\":\"MARGIN_RELEASE\",\"runnerId\":null}",
                DlqReason.RETRY_EXHAUSTED
        );

        assertFalse(adapter.supports(entry));
    }

    @Test
    void reprocessShouldReturnNotAppliedWhenMarginReplayWouldBeSkipped() {
        CapitalDeadLetterReprocessingAdapter adapter = new CapitalDeadLetterReprocessingAdapter(
                executionReaction,
                marginReaction,
                codec,
                runnerRepository,
                globalBalanceRepository
        );
        MarginReleaseEvent original = marginEvent();
        UUID portfolioId = UUID.randomUUID();
        when(runnerRepository.findById(original.release().runnerId()))
                .thenReturn(Optional.of(activeRunner(portfolioId, original.release().runnerId())));
        when(globalBalanceRepository.findByPortfolioId(portfolioId))
                .thenReturn(Optional.of(balanceWithReserved(portfolioId, new BigDecimal("10.00"))));
        DeadLetterEntry entry = new DeadLetterEntry(
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                null,
                codec.encodeMarginRelease(original, new RuntimeException("temporary failure")),
                DlqReason.RETRY_EXHAUSTED
        );

        DeadLetterReprocessingResult result = adapter.reprocess(entry);

        assertFalse(result.applied());
        assertEquals("Dead letter replay produced no state change and requires manual review", result.message());
        verifyNoInteractions(marginReaction);
        verifyNoInteractions(executionReaction);
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
                Instant.now(),
                null,
                null,
                0L
        );
    }

    private static GlobalBalance balanceWithReserved(UUID portfolioId, BigDecimal reserved) {
        return new GlobalBalance(
                portfolioId,
                new BigDecimal("1000.00"),
                reserved,
                BigDecimal.ZERO,
                new BigDecimal("1000.00"),
                "USDT",
                BigDecimal.ZERO,
                null,
                Instant.now(),
                0L
        );
    }
}
