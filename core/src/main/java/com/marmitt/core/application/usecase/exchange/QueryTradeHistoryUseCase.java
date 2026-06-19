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
 * <p><b>Fundação (T28):</b> nenhuma exchange liga {@code tradeHistory()} ainda, então
 * {@code hasTradeHistory()} é {@code false} e o caso de uso sinaliza "não implementado"
 * via {@link UnsupportedOperationException}. T30 liga o {@code BinanceTradeHistoryAdapter}
 * existente ao accessor; o despacho aqui passa a retornar fills reais sem alteração.
 */
public class QueryTradeHistoryUseCase implements QueryTradeHistoryPort {

    private final ExchangeAdapterRepositoryPort exchangeAdapterRepository;

    public QueryTradeHistoryUseCase(ExchangeAdapterRepositoryPort exchangeAdapterRepository) {
        this.exchangeAdapterRepository = exchangeAdapterRepository;
    }

    @Override
    public List<TradeExecutionDto> queryTrades(String exchangeName, String symbol, Instant from, Instant to) {
        ExchangeAdapterDescriptor descriptor = resolveAdapter(exchangeName);

        if (!descriptor.hasTradeHistory()) {
            throw new UnsupportedOperationException(
                    "Trade history query capability is not available for exchange '" + exchangeName + "'");
        }

        return descriptor.tradeHistory().fetchTrades(symbol, from, to);
    }

    private ExchangeAdapterDescriptor resolveAdapter(String exchangeName) {
        return exchangeAdapterRepository.findAdapter(exchangeName)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Exchange '" + exchangeName + "' is not registered"));
    }
}
