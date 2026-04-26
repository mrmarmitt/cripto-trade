package com.marmitt.core.application.usecase.portfolio;

import com.marmitt.core.domain.portfolio.DeadLetterEntry;
import com.marmitt.core.dto.portfolio.response.DeadLetterEntryDto;
import com.marmitt.core.dto.portfolio.DeadLetterReprocessingResult;
import com.marmitt.core.dto.portfolio.response.ReprocessDeadLetterResponse;
import com.marmitt.core.dto.portfolio.response.ResolveDeadLetterResponse;
import com.marmitt.core.enums.DlqReason;
import com.marmitt.core.ports.outbound.repository.DeadLetterReprocessingPort;
import com.marmitt.core.ports.outbound.repository.DeadLetterEntryRepositoryPort;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ManageDeadLetterUseCaseTest {

    @Test
    void listUnresolvedShouldApplyDefaultLimitWhenRequestIsNonPositive() {
        DeadLetterEntryRepositoryPort repository = mock(DeadLetterEntryRepositoryPort.class);
        DeadLetterReprocessingPort reprocessingPort = mock(DeadLetterReprocessingPort.class);
        UUID portfolioId = UUID.randomUUID();
        UUID runnerId = UUID.randomUUID();
        DeadLetterEntry entry = newEntry(portfolioId, runnerId, "client-1");
        when(repository.findUnresolved(portfolioId, runnerId, 100)).thenReturn(List.of(entry));

        ManageDeadLetterUseCase useCase = new ManageDeadLetterUseCase(repository, reprocessingPort);

        List<DeadLetterEntryDto> result = useCase.listUnresolved(portfolioId, runnerId, 0);

        assertEquals(1, result.size());
        assertEquals(entry.getId(), result.getFirst().id());
        verify(repository).findUnresolved(portfolioId, runnerId, 100);
    }

    @Test
    void listUnresolvedShouldCapLimitAtMaximum() {
        DeadLetterEntryRepositoryPort repository = mock(DeadLetterEntryRepositoryPort.class);
        DeadLetterReprocessingPort reprocessingPort = mock(DeadLetterReprocessingPort.class);
        UUID portfolioId = UUID.randomUUID();
        when(repository.findUnresolved(portfolioId, null, 500)).thenReturn(List.of());

        ManageDeadLetterUseCase useCase = new ManageDeadLetterUseCase(repository, reprocessingPort);

        useCase.listUnresolved(portfolioId, null, 999);

        verify(repository).findUnresolved(portfolioId, null, 500);
    }

    @Test
    void resolveShouldMarkEntryResolvedAndPersistIt() {
        DeadLetterEntryRepositoryPort repository = mock(DeadLetterEntryRepositoryPort.class);
        DeadLetterReprocessingPort reprocessingPort = mock(DeadLetterReprocessingPort.class);
        DeadLetterEntry entry = newEntry(UUID.randomUUID(), UUID.randomUUID(), "client-2");
        when(repository.findById(entry.getId())).thenReturn(Optional.of(entry));

        ManageDeadLetterUseCase useCase = new ManageDeadLetterUseCase(repository, reprocessingPort);

        ResolveDeadLetterResponse response = useCase.resolve(entry.getId(), "operator@test", "manual review");

        assertTrue(response.resolved());
        assertEquals("Dead letter entry resolved successfully", response.message());
        assertNotNull(response.entry());
        assertEquals(entry.getId(), response.entry().id());
        assertEquals("operator@test", response.entry().resolvedBy());
        assertNotNull(response.entry().resolvedAt());

        ArgumentCaptor<DeadLetterEntry> entryCaptor = ArgumentCaptor.forClass(DeadLetterEntry.class);
        verify(repository).save(entryCaptor.capture());
        assertTrue(entryCaptor.getValue().isResolved());
        assertEquals("operator@test", entryCaptor.getValue().getResolvedBy());
    }

    @Test
    void resolveShouldRejectBlankOperator() {
        DeadLetterEntryRepositoryPort repository = mock(DeadLetterEntryRepositoryPort.class);
        DeadLetterReprocessingPort reprocessingPort = mock(DeadLetterReprocessingPort.class);
        ManageDeadLetterUseCase useCase = new ManageDeadLetterUseCase(repository, reprocessingPort);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> useCase.resolve(UUID.randomUUID(), "   ", "manual review")
        );

        assertEquals("resolvedBy cannot be blank", error.getMessage());
    }

    @Test
    void resolveShouldReturnFailureWhenEntryDoesNotExist() {
        DeadLetterEntryRepositoryPort repository = mock(DeadLetterEntryRepositoryPort.class);
        DeadLetterReprocessingPort reprocessingPort = mock(DeadLetterReprocessingPort.class);
        UUID deadLetterId = UUID.randomUUID();
        when(repository.findById(deadLetterId)).thenReturn(Optional.empty());

        ManageDeadLetterUseCase useCase = new ManageDeadLetterUseCase(repository, reprocessingPort);

        ResolveDeadLetterResponse response = useCase.resolve(deadLetterId, "operator@test", "manual review");

        assertFalse(response.resolved());
        assertEquals("Dead letter entry not found", response.message());
        assertNull(response.entry());
    }

    @Test
    void resolveShouldReturnFailureWhenEntryIsAlreadyResolved() {
        DeadLetterEntryRepositoryPort repository = mock(DeadLetterEntryRepositoryPort.class);
        DeadLetterReprocessingPort reprocessingPort = mock(DeadLetterReprocessingPort.class);
        DeadLetterEntry entry = newEntry(UUID.randomUUID(), UUID.randomUUID(), "client-3");
        entry.resolve("operator@test");
        when(repository.findById(entry.getId())).thenReturn(Optional.of(entry));

        ManageDeadLetterUseCase useCase = new ManageDeadLetterUseCase(repository, reprocessingPort);

        ResolveDeadLetterResponse response = useCase.resolve(entry.getId(), "another@test", "manual review");

        assertFalse(response.resolved());
        assertEquals("Dead letter entry is already resolved", response.message());
        assertNull(response.entry());
    }

    @Test
    void reprocessShouldReapplySupportedEntryAndResolveIt() {
        DeadLetterEntryRepositoryPort repository = mock(DeadLetterEntryRepositoryPort.class);
        DeadLetterReprocessingPort reprocessingPort = mock(DeadLetterReprocessingPort.class);
        DeadLetterEntry entry = newRetryExhaustedEntry(UUID.randomUUID(), UUID.randomUUID(), "client-4");
        when(repository.findById(entry.getId())).thenReturn(Optional.of(entry));
        when(reprocessingPort.supports(entry)).thenReturn(true);
        when(reprocessingPort.reprocess(entry)).thenReturn(DeadLetterReprocessingResult.applied("Replay applied"));

        ManageDeadLetterUseCase useCase = new ManageDeadLetterUseCase(repository, reprocessingPort);

        ReprocessDeadLetterResponse response = useCase.reprocess(entry.getId(), "operator@test", "retry capital flow");

        assertTrue(response.reprocessed());
        assertEquals("Dead letter entry reprocessed successfully", response.message());
        assertEquals("operator@test", response.entry().resolvedBy());
        verify(reprocessingPort).reprocess(entry);
        verify(repository).save(entry);
    }

    @Test
    void reprocessShouldReturnFailureWhenEntryDoesNotExist() {
        DeadLetterEntryRepositoryPort repository = mock(DeadLetterEntryRepositoryPort.class);
        DeadLetterReprocessingPort reprocessingPort = mock(DeadLetterReprocessingPort.class);
        UUID deadLetterId = UUID.randomUUID();
        when(repository.findById(deadLetterId)).thenReturn(Optional.empty());

        ManageDeadLetterUseCase useCase = new ManageDeadLetterUseCase(repository, reprocessingPort);

        ReprocessDeadLetterResponse response = useCase.reprocess(deadLetterId, "operator@test", "retry capital flow");

        assertFalse(response.reprocessed());
        assertEquals("Dead letter entry not found", response.message());
        assertNull(response.entry());
    }

    @Test
    void reprocessShouldReturnFailureWhenEntryIsAlreadyResolved() {
        DeadLetterEntryRepositoryPort repository = mock(DeadLetterEntryRepositoryPort.class);
        DeadLetterReprocessingPort reprocessingPort = mock(DeadLetterReprocessingPort.class);
        DeadLetterEntry entry = newRetryExhaustedEntry(UUID.randomUUID(), UUID.randomUUID(), "client-5");
        entry.resolve("operator@test");
        when(repository.findById(entry.getId())).thenReturn(Optional.of(entry));

        ManageDeadLetterUseCase useCase = new ManageDeadLetterUseCase(repository, reprocessingPort);

        ReprocessDeadLetterResponse response = useCase.reprocess(entry.getId(), "operator@test", "retry capital flow");

        assertFalse(response.reprocessed());
        assertEquals("Dead letter entry is already resolved", response.message());
        verify(reprocessingPort, never()).reprocess(entry);
    }

    @Test
    void reprocessShouldReturnFailureWhenEntryIsNotSupported() {
        DeadLetterEntryRepositoryPort repository = mock(DeadLetterEntryRepositoryPort.class);
        DeadLetterReprocessingPort reprocessingPort = mock(DeadLetterReprocessingPort.class);
        DeadLetterEntry entry = newEntry(UUID.randomUUID(), UUID.randomUUID(), "client-6");
        when(repository.findById(entry.getId())).thenReturn(Optional.of(entry));
        when(reprocessingPort.supports(entry)).thenReturn(false);

        ManageDeadLetterUseCase useCase = new ManageDeadLetterUseCase(repository, reprocessingPort);

        ReprocessDeadLetterResponse response = useCase.reprocess(entry.getId(), "operator@test", "retry capital flow");

        assertFalse(response.reprocessed());
        assertEquals("Dead letter entry cannot be reprocessed automatically", response.message());
        verify(reprocessingPort, never()).reprocess(entry);
    }

    @Test
    void reprocessShouldReturnFailureWhenReplayProducesNoStateChange() {
        DeadLetterEntryRepositoryPort repository = mock(DeadLetterEntryRepositoryPort.class);
        DeadLetterReprocessingPort reprocessingPort = mock(DeadLetterReprocessingPort.class);
        DeadLetterEntry entry = newRetryExhaustedEntry(UUID.randomUUID(), UUID.randomUUID(), "client-7");
        when(repository.findById(entry.getId())).thenReturn(Optional.of(entry));
        when(reprocessingPort.supports(entry)).thenReturn(true);
        when(reprocessingPort.reprocess(entry)).thenReturn(
                DeadLetterReprocessingResult.notApplied("Dead letter replay produced no state change and requires manual review")
        );

        ManageDeadLetterUseCase useCase = new ManageDeadLetterUseCase(repository, reprocessingPort);

        ReprocessDeadLetterResponse response = useCase.reprocess(entry.getId(), "operator@test", "retry capital flow");

        assertFalse(response.reprocessed());
        assertEquals("Dead letter replay produced no state change and requires manual review", response.message());
        verify(repository, never()).save(entry);
    }

    @Test
    void reprocessShouldRejectBlankRequester() {
        DeadLetterEntryRepositoryPort repository = mock(DeadLetterEntryRepositoryPort.class);
        DeadLetterReprocessingPort reprocessingPort = mock(DeadLetterReprocessingPort.class);
        ManageDeadLetterUseCase useCase = new ManageDeadLetterUseCase(repository, reprocessingPort);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> useCase.reprocess(UUID.randomUUID(), " ", "retry capital flow")
        );

        assertEquals("requestedBy cannot be blank", error.getMessage());
    }

    private static DeadLetterEntry newEntry(UUID portfolioId, UUID runnerId, String clientOrderId) {
        return new DeadLetterEntry(
                portfolioId,
                runnerId,
                clientOrderId,
                "EX_" + clientOrderId,
                "{\"source\":\"test\"}",
                DlqReason.RECONCILIATION_CONFLICT
        );
    }

    private static DeadLetterEntry newRetryExhaustedEntry(UUID portfolioId, UUID runnerId, String clientOrderId) {
        return new DeadLetterEntry(
                portfolioId,
                runnerId,
                clientOrderId,
                "EX_" + clientOrderId,
                "{\"eventType\":\"MARGIN_RELEASE\"}",
                DlqReason.RETRY_EXHAUSTED
        );
    }
}

