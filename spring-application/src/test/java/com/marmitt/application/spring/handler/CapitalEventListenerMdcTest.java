package com.marmitt.application.spring.handler;

import com.marmitt.application.spring.deadletter.CapitalDeadLetterPayloadCodec;
import com.marmitt.core.application.reaction.ExecutionConfirmedReaction;
import com.marmitt.core.application.reaction.MarginReleasedReaction;
import com.marmitt.core.domain.runner.StrategyRunner;
import com.marmitt.core.dto.capital.MarginRelease;
import com.marmitt.core.dto.events.MarginReleaseEvent;
import com.marmitt.core.enums.ReleaseReason;
import com.marmitt.core.ports.outbound.notification.ErrorNotificationPort;
import com.marmitt.core.ports.outbound.repository.DeadLetterEntryRepositoryPort;
import com.marmitt.core.ports.outbound.repository.StrategyRunnerRepositoryPort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * T26: o CapitalEventListener deve ancorar o {@code transactionId} no MDC durante o
 * processamento do evento — inclusive no caminho {@code @Recover}/DLQ — e limpa-lo ao final.
 */
class CapitalEventListenerMdcTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void putsTransactionIdInMdcWhileHandlingMarginRelease() {
        MarginReleasedReaction handleMarginRelease = mock(MarginReleasedReaction.class);
        CapitalEventListener listener = newListener(
                mock(ExecutionConfirmedReaction.class), handleMarginRelease,
                mock(StrategyRunnerRepositoryPort.class), mock(DeadLetterEntryRepositoryPort.class),
                mock(CapitalDeadLetterPayloadCodec.class), mock(ErrorNotificationPort.class));

        UUID transactionId = UUID.randomUUID();
        AtomicReference<String> mdcDuringHandle = new AtomicReference<>();
        doAnswer(invocation -> {
            mdcDuringHandle.set(MDC.get("transactionId"));
            return null;
        }).when(handleMarginRelease).handle(any());

        listener.onMarginRelease(marginReleaseEvent(transactionId));

        assertEquals(transactionId.toString(), mdcDuringHandle.get());
        assertNull(MDC.get("transactionId"), "MDC deve ser limpo apos o processamento");
    }

    @Test
    void putsTransactionIdInMdcWhileRecoveringToDlq() {
        StrategyRunnerRepositoryPort runnerRepository = mock(StrategyRunnerRepositoryPort.class);
        CapitalDeadLetterPayloadCodec codec = mock(CapitalDeadLetterPayloadCodec.class);
        CapitalEventListener listener = newListener(
                mock(ExecutionConfirmedReaction.class), mock(MarginReleasedReaction.class),
                runnerRepository, mock(DeadLetterEntryRepositoryPort.class),
                codec, mock(ErrorNotificationPort.class));

        UUID transactionId = UUID.randomUUID();
        when(codec.encodeMarginRelease(any(), any())).thenReturn("payload");

        AtomicReference<String> mdcDuringRecover = new AtomicReference<>();
        StrategyRunner runner = mock(StrategyRunner.class);
        when(runner.getPortfolioId()).thenReturn(UUID.randomUUID());
        doAnswer(invocation -> {
            mdcDuringRecover.set(MDC.get("transactionId"));
            return Optional.of(runner);
        }).when(runnerRepository).findById(any());

        listener.recoverMarginRelease(new RuntimeException("boom"), marginReleaseEvent(transactionId));

        assertEquals(transactionId.toString(), mdcDuringRecover.get(),
                "transactionId deve estar no MDC durante o caminho @Recover/DLQ");
        assertNull(MDC.get("transactionId"));
    }

    private static MarginReleaseEvent marginReleaseEvent(UUID transactionId) {
        return new MarginReleaseEvent(MarginRelease.fullRelease(
                transactionId, UUID.randomUUID(), new BigDecimal("100.00"), ReleaseReason.EXPIRED));
    }

    private static CapitalEventListener newListener(
            ExecutionConfirmedReaction executionConfirmed,
            MarginReleasedReaction marginRelease,
            StrategyRunnerRepositoryPort runnerRepository,
            DeadLetterEntryRepositoryPort deadLetterRepository,
            CapitalDeadLetterPayloadCodec codec,
            ErrorNotificationPort errorNotification) {
        return new CapitalEventListener(
                executionConfirmed, marginRelease, runnerRepository,
                deadLetterRepository, codec, errorNotification);
    }
}
