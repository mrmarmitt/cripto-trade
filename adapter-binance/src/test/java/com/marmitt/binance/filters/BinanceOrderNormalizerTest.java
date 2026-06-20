package com.marmitt.binance.filters;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marmitt.binance.rest.HttpClientPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BinanceOrderNormalizerTest {

    private static final String SYMBOL = "BTCUSDT";

    private static final String EXCHANGE_INFO_BTCUSDT = """
            {
              "symbols": [{
                "symbol": "BTCUSDT",
                "filters": [
                  {"filterType":"LOT_SIZE","minQty":"0.00001","maxQty":"9000","stepSize":"0.00001"},
                  {"filterType":"PRICE_FILTER","minPrice":"0.01","maxPrice":"1000000","tickSize":"0.01"},
                  {"filterType":"MIN_NOTIONAL","minNotional":"10.00"}
                ]
              }]
            }
            """;

    private BinanceOrderNormalizer normalizer;

    @BeforeEach
    void setUp() {
        SymbolFilterCache cache = new SymbolFilterCache("http://stub", new StubHttpClient(EXCHANGE_INFO_BTCUSDT),
                new ObjectMapper());
        normalizer = new BinanceOrderNormalizer(cache);
    }

    @Test
    void getExchangeName_returnsBinance() {
        assertEquals("BINANCE", normalizer.getExchangeName());
    }

    @Test
    void normalizeQuantity_floorsToStepSize() {
        // stepSize=0.00001 → 0.001234 / 0.00001 = 123.4 → floor=123 → 0.00123
        assertSameValue("0.00123", normalizer.normalizeQuantity(SYMBOL, new BigDecimal("0.001234")));
    }

    @Test
    void normalizeQuantity_keepsAlignedQuantityUnchanged() {
        assertSameValue("0.001", normalizer.normalizeQuantity(SYMBOL, new BigDecimal("0.001")));
    }

    @Test
    void normalizePrice_roundsToTickSize() {
        assertSameValue("43521.76", normalizer.normalizePrice(SYMBOL, new BigDecimal("43521.755")));
    }

    @Test
    void normalizePrice_returnsNullForMarketOrder() {
        assertNull(normalizer.normalizePrice(SYMBOL, null));
    }

    @Test
    void normalizePrice_returnsZeroUntouched() {
        assertSameValue("0", normalizer.normalizePrice(SYMBOL, BigDecimal.ZERO));
    }

    private static void assertSameValue(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual),
                () -> "expected " + expected + " but was " + actual);
    }

    private record StubHttpClient(String body) implements HttpClientPort {
        @Override
        public HttpResponse get(String url, Map<String, String> headers) {
            return new HttpResponse(200, body);
        }

        @Override
        public HttpResponse post(String url, Map<String, String> headers) throws IOException {
            throw new IOException("unused");
        }

        @Override
        public HttpResponse postForm(String url, Map<String, String> headers, String b) throws IOException {
            throw new IOException("unused");
        }

        @Override
        public HttpResponse put(String url, Map<String, String> headers) throws IOException {
            throw new IOException("unused");
        }

        @Override
        public HttpResponse delete(String url, Map<String, String> headers) throws IOException {
            throw new IOException("unused");
        }
    }
}
