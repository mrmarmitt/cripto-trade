package com.marmitt.core.application.usecase.reconciliation;

import com.marmitt.core.domain.runner.Transaction;
import com.marmitt.core.dto.reconciliation.ReconciliationReportDto;
import com.marmitt.core.dto.reconciliation.ReconciliationRequest;
import com.marmitt.core.dto.reconciliation.TradeExecutionDto;
import com.marmitt.core.enums.TransactionStatus;
import com.marmitt.core.enums.TransactionType;
import com.marmitt.core.ports.outbound.exchange.TradeHistoryQueryPort;
import com.marmitt.core.ports.outbound.repository.StrategyRunnerRepositoryPort;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReconcileTradesUseCaseTest {

    private static final String SYMBOL = "BTCUSDT";
    private static final Instant FROM = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-01-02T00:00:00Z");

    private final TradeHistoryQueryPort tradeHistoryQueryPort = mock(TradeHistoryQueryPort.class);
    private final StrategyRunnerRepositoryPort strategyRunnerRepository = mock(StrategyRunnerRepositoryPort.class);
    private final ReconcileTradesUseCase useCase =
            new ReconcileTradesUseCase(tradeHistoryQueryPort, strategyRunnerRepository);

    @Test
    void reconcile_delegatesToBothPortsWithCorrectArgumentsAndReturnsMatchedReport() {
        ReconciliationRequest request = new ReconciliationRequest(SYMBOL, FROM, TO, true);

        // Exchange fill with orderId=100234; local tx with exchangeOrderId="100234" → MATCHED
        when(tradeHistoryQueryPort.fetchTrades(SYMBOL, FROM, TO)).thenReturn(List.of(fill("0.01")));
        when(strategyRunnerRepository.findFilledBySymbolAndPeriod(SYMBOL, FROM, TO))
                .thenReturn(List.of(localTx("client-1", "0.01")));

        ReconciliationReportDto report = useCase.reconcile(request);

        verify(tradeHistoryQueryPort).fetchTrades(eq(SYMBOL), eq(FROM), eq(TO));
        verify(strategyRunnerRepository).findFilledBySymbolAndPeriod(eq(SYMBOL), eq(FROM), eq(TO));
        assertEquals(1, report.summary().matched());
    }

    @Test
    void reconcile_returnsEmptyReportWhenBothPortsReturnEmpty() {
        ReconciliationRequest request = new ReconciliationRequest(SYMBOL, FROM, TO, false);
        when(tradeHistoryQueryPort.fetchTrades(SYMBOL, FROM, TO)).thenReturn(List.of());
        when(strategyRunnerRepository.findFilledBySymbolAndPeriod(SYMBOL, FROM, TO)).thenReturn(List.of());

        ReconciliationReportDto report = useCase.reconcile(request);

        assertEquals(0, report.summary().total());
        assertEquals(SYMBOL, report.symbol());
    }

    @Test
    void reconcile_propagatesExceptionFromExchangePort() {
        ReconciliationRequest request = new ReconciliationRequest(SYMBOL, FROM, TO, false);
        when(tradeHistoryQueryPort.fetchTrades(SYMBOL, FROM, TO))
                .thenThrow(new RuntimeException("Exchange unreachable"));

        assertThrows(RuntimeException.class, () -> useCase.reconcile(request));
    }

    // Binance myTrades does not return clientOrderId; matching is done by exchangeOrderId.
    // fill.exchangeOrderId=100234 matches localTx.exchangeOrderId="100234".
    private static TradeExecutionDto fill(String qty) {
        BigDecimal amount = new BigDecimal(qty);
        return new TradeExecutionDto(
                "trade-1", 100234L, null, "BTCUSDT",
                new BigDecimal("50000"), amount,
                amount.multiply(new BigDecimal("50000")),
                new BigDecimal("0.0001"), "BNB",
                Instant.parse("2026-01-01T10:00:00Z"), true);
    }

    private static Transaction localTx(String clientOrderId, String qty) {
        BigDecimal amount = new BigDecimal(qty);
        return Transaction.reconstitute()
                .id(UUID.randomUUID())
                .runnerId(UUID.randomUUID())
                .clientOrderId(clientOrderId)
                .exchangeOrderId("100234")
                .status(TransactionStatus.FILLED)
                .type(TransactionType.BUY)
                .symbol("BTCUSDT")
                .quantity(amount)
                .executedQuantity(amount)
                .price(new BigDecimal("50000"))
                .executedPrice(new BigDecimal("50000"))
                .total(amount.multiply(new BigDecimal("50000")))
                .confidence(null)
                .reasoning(null)
                .targetLotId(null)
                .requestedAt(Instant.parse("2026-01-01T10:00:00Z"))
                .updatedAt(Instant.parse("2026-01-01T10:00:00Z"))
                .executedAt(Instant.parse("2026-01-01T10:00:00Z"))
                .rejectReason(null)
                .version(0L)
                .build();
    }
}
