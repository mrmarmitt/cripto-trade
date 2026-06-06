package com.marmitt.application.spring.config.exchange;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marmitt.application.spring.adapter.binance.OkHttpClientAdapter;
import com.marmitt.binance.filters.OrderFilterViolationException;
import com.marmitt.binance.filters.OrderFilterValidator;
import com.marmitt.binance.filters.SymbolFilterCache;
import com.marmitt.binance.filters.SymbolFilterLoadException;
import com.marmitt.core.dto.websocket.request.SendOrderRequest;
import com.marmitt.core.enums.OrderSide;
import com.marmitt.core.enums.OrderType;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BinanceSymbolFilterIntegrationTest {

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

    private MockWebServer server;
    private SymbolFilterCache cache;
    private OrderFilterValidator validator;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();

        String baseUrl = "http://" + server.getHostName() + ":" + server.getPort();
        var httpClient = new OkHttpClientAdapter(new OkHttpClient());
        cache = new SymbolFilterCache(baseUrl, httpClient, new ObjectMapper());
        validator = new OrderFilterValidator(cache);
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void shouldPassValidOrderWithoutModification() {
        server.enqueue(new MockResponse().setResponseCode(200).setBody(EXCHANGE_INFO_BTCUSDT));

        SendOrderRequest request = orderRequest("0.001", "50000.00");
        SendOrderRequest result = validator.validate(request);

        assertThat(result.getQuantity()).isEqualByComparingTo("0.001");
        assertThat(result.getPrice()).isEqualByComparingTo("50000.00");
    }

    @Test
    void shouldAdjustQuantityToFloorStepSize() {
        // stepSize=0.00001 → 0.001234 / 0.00001 = 123.4 → floor=123 → 0.00123
        server.enqueue(new MockResponse().setResponseCode(200).setBody(EXCHANGE_INFO_BTCUSDT));

        SendOrderRequest request = orderRequest("0.001234", "50000.00");
        SendOrderRequest result = validator.validate(request);

        assertThat(result.getQuantity()).isEqualByComparingTo("0.00123");
    }

    @Test
    void shouldRejectQuantityBelowMinQtyAfterFloor() {
        String exchangeInfo = exchangeInfoWithLotSize("0.001", "0.001", "9000");
        server.enqueue(new MockResponse().setResponseCode(200).setBody(exchangeInfo));

        SendOrderRequest request = orderRequest("0.0005", "50000.00");

        assertThatThrownBy(() -> validator.validate(request))
                .isInstanceOf(OrderFilterViolationException.class)
                .hasMessageContaining("minQty")
                .hasMessageContaining(SYMBOL);
    }

    @Test
    void shouldRejectOrderWhenNotionalIsBelowMinNotional() {
        server.enqueue(new MockResponse().setResponseCode(200).setBody(EXCHANGE_INFO_BTCUSDT));

        SendOrderRequest request = orderRequest("0.00001", "1.00");

        assertThatThrownBy(() -> validator.validate(request))
                .isInstanceOf(OrderFilterViolationException.class)
                .hasMessageContaining("minNotional")
                .hasMessageContaining(SYMBOL);
    }

    @Test
    void shouldRoundPriceToTickSize() {
        server.enqueue(new MockResponse().setResponseCode(200).setBody(EXCHANGE_INFO_BTCUSDT));

        SendOrderRequest request = orderRequest("0.001", "43521.755");
        SendOrderRequest result = validator.validate(request);

        assertThat(result.getPrice()).isEqualByComparingTo("43521.76");
    }

    @Test
    void shouldThrowOnExchangeInfoHttpFailure() {
        server.enqueue(new MockResponse().setResponseCode(500).setBody("Internal Server Error"));

        assertThatThrownBy(() -> cache.loadAndCache(SYMBOL))
                .isInstanceOf(SymbolFilterLoadException.class)
                .hasMessageContaining(SYMBOL)
                .hasMessageContaining("HTTP 500");
    }

    @Test
    void shouldInvalidateCacheAndReloadFiltersOnNextUse() {
        server.enqueue(new MockResponse().setResponseCode(200).setBody(EXCHANGE_INFO_BTCUSDT));
        cache.loadAndCache(SYMBOL);

        cache.invalidateAll();

        String updatedExchangeInfo = exchangeInfoWithLotSize("0.001", "0.001", "9000");
        server.enqueue(new MockResponse().setResponseCode(200).setBody(updatedExchangeInfo));

        SendOrderRequest request = orderRequest("0.001", "50000.00");
        SendOrderRequest result = validator.validate(request);

        assertThat(result.getQuantity()).isEqualByComparingTo("0.001");
    }

    @Test
    void shouldSkipNotionalCheckForMarketOrders() {
        server.enqueue(new MockResponse().setResponseCode(200).setBody(EXCHANGE_INFO_BTCUSDT));

        SendOrderRequest request = new SendOrderRequest(
                "BINANCE", SYMBOL, new BigDecimal("0.001"), null,
                OrderType.MARKET, OrderSide.BUY, "client-1");

        SendOrderRequest result = validator.validate(request);

        assertThat(result.getQuantity()).isEqualByComparingTo("0.001");
        assertThat(result.getPrice()).isNull();
    }

    private SendOrderRequest orderRequest(String quantity, String price) {
        return new SendOrderRequest(
                "BINANCE", SYMBOL,
                new BigDecimal(quantity), new BigDecimal(price),
                OrderType.LIMIT, OrderSide.BUY, "client-1");
    }

    private String exchangeInfoWithLotSize(String stepSize, String minQty, String maxQty) {
        return """
                {
                  "symbols": [{
                    "symbol": "BTCUSDT",
                    "filters": [
                      {"filterType":"LOT_SIZE","minQty":"%s","maxQty":"%s","stepSize":"%s"},
                      {"filterType":"PRICE_FILTER","minPrice":"0.01","maxPrice":"1000000","tickSize":"0.01"},
                      {"filterType":"MIN_NOTIONAL","minNotional":"10.00"}
                    ]
                  }]
                }
                """.formatted(minQty, maxQty, stepSize);
    }
}
