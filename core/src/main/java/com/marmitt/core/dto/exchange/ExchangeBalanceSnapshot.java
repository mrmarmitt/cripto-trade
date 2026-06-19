package com.marmitt.core.dto.exchange;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Snapshot de saldos de uma exchange num instante, retornado pelo caminho de
 * consulta agnóstico ({@code QueryExchangeBalancePort}).
 *
 * <p>A consulta traz a lista completa numa única chamada (decisão "lista completa +
 * filtro opcional"); o filtro por asset é aplicado na borda via {@link #balanceOf(String)}.
 *
 * @param exchangeName exchange consultada (ex.: "BINANCE")
 * @param balances     saldos por asset
 * @param retrievedAt  instante em que o snapshot foi obtido
 */
public record ExchangeBalanceSnapshot(
        String exchangeName,
        List<BalanceDto> balances,
        Instant retrievedAt
) {

    public ExchangeBalanceSnapshot {
        balances = balances != null ? List.copyOf(balances) : List.of();
    }

    /** Retorna o saldo do {@code asset} informado (case-insensitive), se presente. */
    public Optional<BalanceDto> balanceOf(String asset) {
        if (asset == null) {
            return Optional.empty();
        }
        return balances.stream()
                .filter(b -> Objects.equals(asset.toUpperCase(), b.asset() == null ? null : b.asset().toUpperCase()))
                .findFirst();
    }
}
