package com.marmitt.core.application.usecase.exchange;

import com.marmitt.core.dto.exchange.BalanceDto;
import com.marmitt.core.dto.exchange.ExchangeBalanceSnapshot;
import com.marmitt.core.dto.websocket.data.AccountDataDto;
import com.marmitt.core.ports.inbound.exchange.QueryExchangeBalancePort;
import com.marmitt.core.ports.outbound.exchange.ExchangeAdapterDescriptor;
import com.marmitt.core.ports.outbound.repository.ExchangeAdapterRepositoryPort;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Caso de uso agnóstico de consulta de saldo.
 *
 * <p>Resolve o {@link ExchangeAdapterDescriptor} da exchange pedida e despacha pela
 * capacidade de conta, sem conhecer nenhum adapter concreto. Reusa o snapshot de conta
 * já existente ({@code accountQuery().queryAccountSnapshot()}) — sem rota REST duplicada —
 * e o traduz para o contrato dedicado {@link ExchangeBalanceSnapshot}, evitando vazar
 * {@link AccountDataDto} (DTO de WebSocket) para fora do core.
 *
 * <p>Retorna a lista completa de saldos numa única chamada; o filtro por asset é
 * responsabilidade do consumidor ({@link ExchangeBalanceSnapshot#balanceOf(String)}).
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

        AccountDataDto account = descriptor.accountQuery().queryAccountSnapshot();
        return toSnapshot(descriptor.exchangeName(), account);
    }

    private static ExchangeBalanceSnapshot toSnapshot(String exchangeName, AccountDataDto account) {
        Map<String, BigDecimal> free = account.balances() != null ? account.balances() : Map.of();
        Map<String, BigDecimal> locked = account.lockedBalances() != null ? account.lockedBalances() : Map.of();

        // Union of both maps so an asset present in only one side is still reported.
        TreeSet<String> assets = new TreeSet<>();
        assets.addAll(free.keySet());
        assets.addAll(locked.keySet());

        List<BalanceDto> balances = assets.stream()
                .map(asset -> BalanceDto.of(asset, free.get(asset), locked.get(asset)))
                .toList();

        return new ExchangeBalanceSnapshot(exchangeName, balances, account.lastUpdateTime());
    }

    private ExchangeAdapterDescriptor resolveAdapter(String exchangeName) {
        return exchangeAdapterRepository.findAdapter(exchangeName)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Exchange '" + exchangeName + "' is not registered"));
    }
}
