package com.marmitt.core.application.usecase.portfolio;

import com.marmitt.core.domain.portfolio.Portfolio;
import com.marmitt.core.enums.SafeModeStatus;
import com.marmitt.core.enums.SafeModeTrigger;
import com.marmitt.core.ports.outbound.repository.DeadLetterEntryRepositoryPort;
import com.marmitt.core.ports.outbound.repository.PortfolioRepositoryPort;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T25: gatilho de DLQ por portfolio.
 */
class EvaluateOperationalTriggersUseCaseTest {

    private final PortfolioRepositoryPort portfolios = mock(PortfolioRepositoryPort.class);
    private final DeadLetterEntryRepositoryPort deadLetters = mock(DeadLetterEntryRepositoryPort.class);
    private final AutomaticSafeModeTrigger trigger = mock(AutomaticSafeModeTrigger.class);
    private final EvaluateOperationalTriggersUseCase useCase =
            new EvaluateOperationalTriggersUseCase(portfolios, deadLetters, trigger, 3);

    @Test
    void thresholdOfUnresolvedEntriesTriggersHalt() {
        Portfolio portfolio = portfolio(SafeModeStatus.NORMAL);
        when(portfolios.findAll()).thenReturn(List.of(portfolio));
        when(deadLetters.countUnresolvedByPortfolioId(portfolio.getId())).thenReturn(3L);

        useCase.evaluate();

        verify(trigger).activate(eq(portfolio.getId()), eq(SafeModeTrigger.DLQ_THRESHOLD), any());
    }

    @Test
    void belowThresholdOrAlreadyInSafeModeDoesNothing() {
        Portfolio below = portfolio(SafeModeStatus.NORMAL);
        Portfolio halted = portfolio(SafeModeStatus.HALT);
        when(portfolios.findAll()).thenReturn(List.of(below, halted));
        when(deadLetters.countUnresolvedByPortfolioId(below.getId())).thenReturn(2L);

        useCase.evaluate();

        verify(trigger, never()).activate(any(), any(), any());
        verify(deadLetters, never()).countUnresolvedByPortfolioId(halted.getId());
    }

    private static Portfolio portfolio(SafeModeStatus level) {
        Portfolio portfolio = mock(Portfolio.class);
        when(portfolio.getId()).thenReturn(UUID.randomUUID());
        when(portfolio.isActive()).thenReturn(true);
        when(portfolio.getSafeModeStatus()).thenReturn(level);
        return portfolio;
    }
}
