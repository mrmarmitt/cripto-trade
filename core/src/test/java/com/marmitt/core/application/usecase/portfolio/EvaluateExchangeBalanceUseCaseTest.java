package com.marmitt.core.application.usecase.portfolio;

import com.marmitt.core.application.usecase.exchange.ExchangeBalanceReconciliation;
import com.marmitt.core.dto.exchange.ExchangeBalanceReport;
import com.marmitt.core.enums.ExchangeBalanceStatus;
import com.marmitt.core.enums.SafeModeTrigger;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EvaluateExchangeBalanceUseCaseTest {

    private final ExchangeBalanceReconciliation reconciliation = mock(ExchangeBalanceReconciliation.class);
    private final AutomaticSafeModeTrigger trigger = mock(AutomaticSafeModeTrigger.class);
    private final EvaluateExchangeBalanceUseCase useCase = new EvaluateExchangeBalanceUseCase(reconciliation, trigger);

    private final UUID first = UUID.randomUUID();
    private final UUID second = UUID.randomUUID();

    @Test
    void deficitHaltsEveryPortfolioOfTheExchangeOnlyOnTheSecondReadingInARow() {
        when(reconciliation.reconcileAll()).thenReturn(List.of(report(ExchangeBalanceStatus.DEFICIT)));

        useCase.evaluate();
        verify(trigger, never()).activate(any(), any(), anyString());

        useCase.evaluate();
        verify(trigger).activate(eq(first), eq(SafeModeTrigger.BALANCE_DIVERGENCE), anyString());
        verify(trigger).activate(eq(second), eq(SafeModeTrigger.BALANCE_DIVERGENCE), anyString());
    }

    @Test
    void transientDeficitBetweenReadingsDoesNotHalt() {
        when(reconciliation.reconcileAll()).thenReturn(
                List.of(report(ExchangeBalanceStatus.DEFICIT)),
                List.of(report(ExchangeBalanceStatus.OK)),
                List.of(report(ExchangeBalanceStatus.DEFICIT)));

        useCase.evaluate();
        useCase.evaluate();
        useCase.evaluate();

        verify(trigger, never()).activate(any(), any(), anyString());
    }

    @Test
    void pendingNeverHalts() {
        when(reconciliation.reconcileAll()).thenReturn(List.of(report(ExchangeBalanceStatus.PENDING)));

        useCase.evaluate();
        useCase.evaluate();

        verify(trigger, never()).activate(any(), any(), anyString());
    }

    private ExchangeBalanceReport report(ExchangeBalanceStatus status) {
        BigDecimal delta = status == ExchangeBalanceStatus.OK ? BigDecimal.ZERO : new BigDecimal("-100");
        return ExchangeBalanceReport.checked("BINANCE", List.of(first, second), List.of(
                new ExchangeBalanceReport.AssetBalance("USDT", new BigDecimal("2000"),
                        new BigDecimal("2000").add(delta), delta, BigDecimal.ZERO, BigDecimal.ZERO, status)));
    }
}
