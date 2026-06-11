package com.marmitt.core.ports.outbound.exchange;

import com.marmitt.core.dto.reconciliation.TradeExecutionDto;

import java.time.Instant;
import java.util.List;

/**
 * Outbound port for fetching historical trade fills from the exchange.
 * Each entry represents a single fill event; one order may produce multiple entries.
 */
public interface TradeHistoryQueryPort {

    /** Exchange identifier this adapter serves (e.g. "BINANCE"). Case-insensitive. */
    String getExchangeName();

    /**
     * Fetches all fills for {@code symbol} executed within [{@code from}, {@code to}].
     * Multiple fills for the same order (partial fills) are returned as separate entries.
     *
     * @throws RuntimeException if the exchange is unreachable or returns an error
     */
    List<TradeExecutionDto> fetchTrades(String symbol, Instant from, Instant to);
}
