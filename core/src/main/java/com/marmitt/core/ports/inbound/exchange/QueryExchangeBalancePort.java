package com.marmitt.core.ports.inbound.exchange;

import com.marmitt.core.dto.exchange.ExchangeBalanceSnapshot;

/**
 * Caso de uso de consulta de saldo na exchange, agnóstico ao adapter.
 *
 * <p>Resolve o adapter pela {@code exchangeName} e retorna a lista completa de saldos
 * numa única chamada; o filtro por asset é responsabilidade do consumidor
 * ({@link ExchangeBalanceSnapshot#balanceOf(String)} ou query param na borda HTTP).
 */
public interface QueryExchangeBalancePort {

    /**
     * @param exchangeName exchange alvo (ex.: "BINANCE"), case-insensitive
     * @return snapshot completo de saldos da exchange
     * @throws IllegalArgumentException      se a exchange não estiver registrada
     * @throws UnsupportedOperationException se a exchange não suportar consulta de saldo
     */
    ExchangeBalanceSnapshot queryBalances(String exchangeName);
}
