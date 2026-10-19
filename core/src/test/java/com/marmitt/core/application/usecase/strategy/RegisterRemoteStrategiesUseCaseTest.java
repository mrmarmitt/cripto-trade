package com.marmitt.core.application.usecase.strategy;

import com.marmitt.core.dto.strategy.RemoteStrategyDefinition;
import com.marmitt.core.exceptions.StrategyUnavailableException;
import com.marmitt.core.ports.outbound.repository.StrategyRepositoryPort;
import com.marmitt.core.ports.outbound.strategy.StrategyEvaluationPort;
import com.marmitt.core.ports.outbound.strategy.StrategyEvaluationPort.RemoteStrategyIdentity;
import com.marmitt.core.ports.outbound.strategy.TradingStrategy;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T41: o catalogo so registra a versao cuja identidade o proprio servico confirma.
 */
class RegisterRemoteStrategiesUseCaseTest {

    private static final UUID REF = UUID.randomUUID();
    private static final RemoteStrategyDefinition SMA = new RemoteStrategyDefinition(REF, "SMA", "1.0.0");

    private final StrategyRepositoryPort repository = mock(StrategyRepositoryPort.class);
    private final StrategyEvaluationPort evaluation = mock(StrategyEvaluationPort.class);
    private final RegisterRemoteStrategiesUseCase useCase = new RegisterRemoteStrategiesUseCase(repository, evaluation);

    @Test
    void registersWhenTheServiceConfirmsTheIdentity() {
        when(repository.findById(REF)).thenReturn(Optional.empty());
        when(evaluation.describe(REF)).thenReturn(new RemoteStrategyIdentity(REF, "SMA", "1.0.0", "1.1.0"));

        assertEquals(List.of(REF), useCase.registerPending(List.of(SMA)));

        ArgumentCaptor<TradingStrategy> registered = ArgumentCaptor.forClass(TradingStrategy.class);
        verify(repository).registerStrategy(registered.capture());
        assertEquals(REF, registered.getValue().getStrategyId());
        assertTrue(registered.getValue() instanceof RemoteTradingStrategy);
    }

    @Test
    void divergentIdentityOrContractIsNotRegistered() {
        when(repository.findById(REF)).thenReturn(Optional.empty());

        when(evaluation.describe(REF)).thenReturn(new RemoteStrategyIdentity(REF, "SMA", "2.0.0", "1.1.0"));
        assertTrue(useCase.registerPending(List.of(SMA)).isEmpty(), "versao diferente do catalogo");
        when(evaluation.describe(REF)).thenReturn(new RemoteStrategyIdentity(REF, "SMA", "1.0.0", "2.0.0"));
        assertTrue(useCase.registerPending(List.of(SMA)).isEmpty(), "contrato de outra versao maior");
        when(evaluation.describe(REF)).thenReturn(new RemoteStrategyIdentity(UUID.randomUUID(), "SMA", "1.0.0", "1.1.0"));
        assertTrue(useCase.registerPending(List.of(SMA)).isEmpty(), "outro strategyId");

        verify(repository, never()).registerStrategy(any());
    }

    @Test
    void unavailableServiceIsRetriedOnTheNextCall() {
        when(repository.findById(REF)).thenReturn(Optional.empty());
        when(evaluation.describe(REF))
                .thenThrow(new StrategyUnavailableException(StrategyUnavailableException.Reason.UNREACHABLE, "down"))
                .thenReturn(new RemoteStrategyIdentity(REF, "SMA", "1.0.0", "1.1.0"));

        assertTrue(useCase.registerPending(List.of(SMA)).isEmpty());
        assertEquals(List.of(REF), useCase.registerPending(List.of(SMA)));
    }

    @Test
    void alreadyRegisteredEntryIsSkipped() {
        when(repository.findById(REF)).thenReturn(Optional.of(mock(TradingStrategy.class)));

        assertTrue(useCase.registerPending(List.of(SMA)).isEmpty());
        verify(evaluation, never()).describe(any());
    }
}
