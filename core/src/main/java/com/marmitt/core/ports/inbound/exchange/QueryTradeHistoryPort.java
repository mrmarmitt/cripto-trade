package com.marmitt.core.ports.inbound.exchange;

import com.marmitt.core.dto.reconciliation.TradeExecutionDto;

import java.time.Instant;
import java.util.List;

/**
 * Caso de uso de consulta de histórico de trades na exchange, agnóstico ao adapter.
 *
 * <p>Resolve o adapter pela {@code exchangeName} e delega a busca de fills à capacidade
 * de histórico do descriptor. Cada fill é um {@link TradeExecutionDto}; uma ordem pode
 * gerar múltiplos fills (parciais).
 */
public interface QueryTradeHistoryPort {

    /**
     * @param exchangeName exchange alvo (ex.: "BINANCE"), case-insensitive
     * @param symbol       par negociado (ex.: "BTCUSDT")
     * @param from         início da janela (inclusivo)
     * @param to           fim da janela (inclusivo)
     * @return fills executados na janela
     * @throws IllegalArgumentException      se a exchange não estiver registrada
     * @throws UnsupportedOperationException se a exchange não suportar consulta de histórico
     */
    List<TradeExecutionDto> queryTrades(String exchangeName, String symbol, Instant from, Instant to);
}
