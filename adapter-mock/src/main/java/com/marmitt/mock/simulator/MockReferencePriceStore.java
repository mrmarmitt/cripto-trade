package com.marmitt.mock.simulator;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds the latest known market price per symbol, used by the order simulator to decide
 * whether a LIMIT order is marketable or rests on the book.
 *
 * <p>A symbol with no known reference price is intentional and means "no opinion": the
 * simulator falls back to its always-fill behavior. That keeps scenarios that never feed a
 * reference price behaving exactly as before.
 */
public class MockReferencePriceStore {

    private final Map<String, BigDecimal> priceBySymbol = new ConcurrentHashMap<>();

    public void update(String symbol, BigDecimal price) {
        if (symbol == null || symbol.isBlank()) {
            return;
        }
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        priceBySymbol.put(normalize(symbol), price);
    }

    public Optional<BigDecimal> find(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(priceBySymbol.get(normalize(symbol)));
    }

    public void clear() {
        priceBySymbol.clear();
    }

    private static String normalize(String symbol) {
        return symbol.trim().toUpperCase(Locale.ROOT);
    }
}
