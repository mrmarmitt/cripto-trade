package com.marmitt.application.spring.repository;

import com.marmitt.core.ports.outbound.marketdata.LatestPricePort;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Último preço por símbolo em memória (T43). Escrito a cada tick pelo listener de market data,
 * lido pelo monitor de drawdown; não persiste — após restart o monitor espera os primeiros ticks.
 */
@Repository
public class InMemoryLatestPriceRepository implements LatestPricePort {

    private final Map<String, BigDecimal> priceBySymbol = new ConcurrentHashMap<>();
    private final Map<String, Instant> updatedAtBySymbol = new ConcurrentHashMap<>();

    @Override
    public void record(String symbol, BigDecimal price) {
        if (symbol == null || price == null || price.signum() <= 0) {
            return;
        }
        priceBySymbol.put(key(symbol), price);
        updatedAtBySymbol.put(key(symbol), Instant.now());
    }

    @Override
    public Optional<BigDecimal> find(String symbol) {
        return symbol == null ? Optional.empty() : Optional.ofNullable(priceBySymbol.get(key(symbol)));
    }

    @Override
    public Optional<Instant> findUpdatedAt(String symbol) {
        return symbol == null ? Optional.empty() : Optional.ofNullable(updatedAtBySymbol.get(key(symbol)));
    }

    private static String key(String symbol) {
        return symbol.toUpperCase(Locale.ROOT);
    }
}
