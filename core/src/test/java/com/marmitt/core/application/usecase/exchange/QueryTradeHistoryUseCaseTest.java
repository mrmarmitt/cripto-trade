package com.marmitt.core.application.usecase.exchange;

import com.marmitt.core.dto.reconciliation.TradeExecutionDto;
import com.marmitt.core.ports.outbound.exchange.ExchangeAdapterDescriptor;
import com.marmitt.core.ports.outbound.exchange.TradeHistoryQueryPort;
import com.marmitt.core.ports.outbound.repository.ExchangeAdapterRepositoryPort;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QueryTradeHistoryUseCaseTest {

    private static final String EXCHANGE = "BINANCE";
    private static final String SYMBOL = "BTCUSDT";
    private static final Instant FROM = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-01-02T00:00:00Z");

    private final ExchangeAdapterRepositoryPort repository = mock(ExchangeAdapterRepositoryPort.class);
    private final QueryTradeHistoryUseCase useCase = new QueryTradeHistoryUseCase(repository);

    @Test
    void queryTrades_throwsWhenExchangeNotRegistered() {
        when(repository.findAdapter("UNKNOWN")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> useCase.queryTrades("UNKNOWN", SYMBOL, FROM, TO));
    }

    @Test
    void queryTrades_throwsUnsupportedWhenCapabilityAbsent() {
        ExchangeAdapterDescriptor descriptor = mock(ExchangeAdapterDescriptor.class);
        when(descriptor.hasTradeHistory()).thenReturn(false);
        when(repository.findAdapter(EXCHANGE)).thenReturn(Optional.of(descriptor));

        assertThrows(UnsupportedOperationException.class,
                () -> useCase.queryTrades(EXCHANGE, SYMBOL, FROM, TO));
    }

    @Test
    void queryTrades_dispatchesToResolvedAdapterWhenCapabilityPresent() {
        ExchangeAdapterDescriptor descriptor = mock(ExchangeAdapterDescriptor.class);
        TradeHistoryQueryPort tradeHistory = mock(TradeHistoryQueryPort.class);
        List<TradeExecutionDto> fills = List.of(fill());

        when(descriptor.hasTradeHistory()).thenReturn(true);
        when(descriptor.tradeHistory()).thenReturn(tradeHistory);
        when(tradeHistory.fetchTrades(SYMBOL, FROM, TO)).thenReturn(fills);
        when(repository.findAdapter(EXCHANGE)).thenReturn(Optional.of(descriptor));

        List<TradeExecutionDto> result = useCase.queryTrades(EXCHANGE, SYMBOL, FROM, TO);

        assertSame(fills, result);
        verify(tradeHistory).fetchTrades(eq(SYMBOL), eq(FROM), eq(TO));
    }

    private static TradeExecutionDto fill() {
        return new TradeExecutionDto(
                "trade-1", 100234L, null, SYMBOL,
                new BigDecimal("50000"), new BigDecimal("0.01"),
                new BigDecimal("500"), new BigDecimal("0.0001"), "BNB",
                Instant.parse("2026-01-01T10:00:00Z"), true);
    }
}
