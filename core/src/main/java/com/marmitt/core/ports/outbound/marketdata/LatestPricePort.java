package com.marmitt.core.ports.outbound.marketdata;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

/**
 * Último preço conhecido por símbolo, alimentado pelo market data (T43). Fonte única para marcar
 * posições a mercado fora do caminho do tick (ex.: monitor de drawdown).
 *
 * <p>Chave por símbolo, independentemente da exchange de origem do tick: os runners recebem market
 * data de fontes autorizadas que cotam o mesmo par. Volátil por design — após restart fica vazio até
 * os primeiros ticks chegarem.
 */
public interface LatestPricePort {

    void record(String symbol, BigDecimal price);

    Optional<BigDecimal> find(String symbol);

    /**
     * Quando o ultimo preco do simbolo chegou (T55: verificacao de market data parado).
     */
    Optional<Instant> findUpdatedAt(String symbol);
}
