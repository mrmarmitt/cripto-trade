package com.marmitt.core.application.usecase.exchange;

import com.marmitt.core.dto.exchange.ExchangeBalanceSnapshot;
import com.marmitt.core.ports.inbound.exchange.QueryExchangeBalancePort;
import com.marmitt.core.ports.outbound.exchange.ExchangeAdapterDescriptor;
import com.marmitt.core.ports.outbound.repository.ExchangeAdapterRepositoryPort;

/**
 * Caso de uso agnóstico de consulta de saldo.
 *
 * <p>Resolve o {@link ExchangeAdapterDescriptor} da exchange pedida e despacha pela
 * capacidade de conta, sem conhecer nenhum adapter concreto.
 *
 * <p><b>Fundação (T28):</b> o caminho agnóstico está montado, mas a recuperação real do
 * snapshot e o mapeamento {@code AccountDataDto -> ExchangeBalanceSnapshot} são entregues
 * em T29. Até lá, o caso de uso sinaliza "não implementado" via
 * {@link UnsupportedOperationException}, convenção tratada graciosamente pelos consumidores.
 */
public class QueryExchangeBalanceUseCase implements QueryExchangeBalancePort {

    private final ExchangeAdapterRepositoryPort exchangeAdapterRepository;

    public QueryExchangeBalanceUseCase(ExchangeAdapterRepositoryPort exchangeAdapterRepository) {
        this.exchangeAdapterRepository = exchangeAdapterRepository;
    }

    @Override
    public ExchangeBalanceSnapshot queryBalances(String exchangeName) {
        ExchangeAdapterDescriptor descriptor = resolveAdapter(exchangeName);

        if (!descriptor.hasAccountQuery()) {
            throw new UnsupportedOperationException(
                    "Balance query capability is not available for exchange '" + exchangeName + "'");
        }

        // T29: reusar descriptor.accountQuery().queryAccountSnapshot() e mapear
        // AccountDataDto -> ExchangeBalanceSnapshot, sem vazar o DTO de WebSocket.
        throw new UnsupportedOperationException(
                "Balance query is not implemented yet for exchange '" + exchangeName + "'");
    }

    private ExchangeAdapterDescriptor resolveAdapter(String exchangeName) {
        return exchangeAdapterRepository.findAdapter(exchangeName)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Exchange '" + exchangeName + "' is not registered"));
    }
}
