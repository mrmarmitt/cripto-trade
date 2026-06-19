package com.marmitt.core.application.usecase.exchange;

import com.marmitt.core.dto.reconciliation.TradeExecutionDto;
import com.marmitt.core.ports.inbound.exchange.QueryTradeHistoryPort;
import com.marmitt.core.ports.outbound.exchange.ExchangeAdapterDescriptor;
import com.marmitt.core.ports.outbound.repository.ExchangeAdapterRepositoryPort;

import java.time.Instant;
import java.util.List;

/**
 * Caso de uso agnóstico de consulta de histórico de trades.
 *
 * <p>Resolve o {@link ExchangeAdapterDescriptor} da exchange pedida e despacha pela
 * capacidade de histórico, sem conhecer nenhum adapter concreto.
 *
 * <p>Valida o intervalo na borda ({@code symbol}, {@code from <= to}, ambos presentes)
 * antes de delegar. A paginação/limite de janela da exchange é tratada no adapter
 * (ex.: {@code BinanceTradeHistoryAdapter} fatia janelas de 24h internamente).
 */
public class QueryTradeHistoryUseCase implements QueryTradeHistoryPort {

    private final ExchangeAdapterRepositoryPort exchangeAdapterRepository;

    public QueryTradeHistoryUseCase(ExchangeAdapterRepositoryPort exchangeAdapterRepository) {
        this.exchangeAdapterRepository = exchangeAdapterRepository;
    }

    @Override
    public List<TradeExecutionDto> queryTrades(String exchangeName, String symbol, Instant from, Instant to) {
        validateInterval(symbol, from, to);

        ExchangeAdapterDescriptor descriptor = resolveAdapter(exchangeName);

        if (!descriptor.hasTradeHistory()) {
            throw new UnsupportedOperationException(
                    "Trade history query capability is not available for exchange '" + exchangeName + "'");
        }

        return descriptor.tradeHistory().fetchTrades(symbol, from, to);
    }

    private static void validateInterval(String symbol, Instant from, Instant to) {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("symbol must not be blank");
        }
        if (from == null || to == null) {
            throw new IllegalArgumentException("from and to must not be null");
        }
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("from must not be after to (from=" + from + ", to=" + to + ")");
        }
    }

    private ExchangeAdapterDescriptor resolveAdapter(String exchangeName) {
        return exchangeAdapterRepository.findAdapter(exchangeName)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Exchange '" + exchangeName + "' is not registered"));
    }
}
