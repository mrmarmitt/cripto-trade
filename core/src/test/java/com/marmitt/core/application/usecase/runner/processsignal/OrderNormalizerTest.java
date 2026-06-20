package com.marmitt.core.application.usecase.runner.processsignal;

import com.marmitt.core.ports.outbound.exchange.rest.OrderQuantityNormalizerPort;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OrderNormalizerTest {

    private static final String SYMBOL = "BTCUSDT";
    private static final BigDecimal RAW_QTY = new BigDecimal("0.001234");
    private static final BigDecimal RAW_PRICE = new BigDecimal("43521.755");

    @Test
    void normalize_appliesRegisteredNormalizerForExchange() {
        OrderNormalizer normalizer = new OrderNormalizer(List.of(new FixedNormalizer("BINANCE")));

        OrderNormalizer.NormalizedOrder result = normalizer.normalize("BINANCE", SYMBOL, RAW_QTY, RAW_PRICE);

        assertSameValue("0.00123", result.quantity());
        assertSameValue("43521.75", result.price());
    }

    @Test
    void normalize_resolvesExchangeNameCaseInsensitively() {
        OrderNormalizer normalizer = new OrderNormalizer(List.of(new FixedNormalizer("BINANCE")));

        OrderNormalizer.NormalizedOrder result = normalizer.normalize("binance", SYMBOL, RAW_QTY, RAW_PRICE);

        assertSameValue("0.00123", result.quantity());
    }

    @Test
    void normalize_returnsOriginalWhenNoNormalizerForExchange() {
        OrderNormalizer normalizer = new OrderNormalizer(List.of(new FixedNormalizer("BINANCE")));

        OrderNormalizer.NormalizedOrder result = normalizer.normalize("MOCK", SYMBOL, RAW_QTY, RAW_PRICE);

        assertEquals(RAW_QTY, result.quantity());
        assertEquals(RAW_PRICE, result.price());
    }

    @Test
    void normalize_returnsOriginalWhenNoNormalizersRegistered() {
        OrderNormalizer normalizer = new OrderNormalizer(List.of());

        OrderNormalizer.NormalizedOrder result = normalizer.normalize("BINANCE", SYMBOL, RAW_QTY, RAW_PRICE);

        assertEquals(RAW_QTY, result.quantity());
        assertEquals(RAW_PRICE, result.price());
    }

    @Test
    void normalize_returnsOriginalWhenExchangeNameIsNull() {
        OrderNormalizer normalizer = new OrderNormalizer(List.of(new FixedNormalizer("BINANCE")));

        OrderNormalizer.NormalizedOrder result = normalizer.normalize(null, SYMBOL, RAW_QTY, RAW_PRICE);

        assertEquals(RAW_QTY, result.quantity());
    }

    private static void assertSameValue(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual),
                () -> "expected " + expected + " but was " + actual);
    }

    /**
     * Normalizador fake que floor de quantidade para 5 casas e preço para 2 casas,
     * suficiente para verificar a delegação sem depender de regras reais de exchange.
     */
    private record FixedNormalizer(String exchangeName) implements OrderQuantityNormalizerPort {
        @Override
        public String getExchangeName() {
            return exchangeName;
        }

        @Override
        public BigDecimal normalizeQuantity(String symbol, BigDecimal quantity) {
            return quantity.setScale(5, java.math.RoundingMode.FLOOR);
        }

        @Override
        public BigDecimal normalizePrice(String symbol, BigDecimal price) {
            return price == null ? null : price.setScale(2, java.math.RoundingMode.FLOOR);
        }
    }
}
