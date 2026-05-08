package com.marmitt.application.spring.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marmitt.application.spring.adapter.binance.OkHttpClientAdapter;
import com.marmitt.binance.auth.BinanceCredentials;
import com.marmitt.binance.auth.BinanceRequestSigner;
import com.marmitt.binance.rest.BinanceRestAdapter;
import com.marmitt.core.dto.websocket.data.AccountDataDto;
import com.marmitt.core.dto.websocket.data.OrderDataDto;
import com.marmitt.core.dto.websocket.request.SendCancelOrderRequest;
import com.marmitt.core.dto.websocket.request.SendOrderRequest;
import com.marmitt.core.enums.OrderSide;
import com.marmitt.core.enums.OrderType;
import com.marmitt.core.exceptions.ExchangeQueryException;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class BinanceRestAdapterIntegrationTest {

    MockWebServer mockServer;
    BinanceRestAdapter adapter;
    ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws IOException {
        mockServer = new MockWebServer();
        mockServer.start();

        var credentials = new BinanceCredentials("test-api-key", "test-secret-key");
        var signer      = new BinanceRequestSigner(credentials);
        var okHttp      = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(5))
                .readTimeout(Duration.ofSeconds(5))
                .build();
        var httpClient  = new OkHttpClientAdapter(okHttp);
        adapter = new BinanceRestAdapter(mockServer.url("").toString().replaceAll("/$", ""),
                signer, httpClient, objectMapper);
    }

    @AfterEach
    void tearDown() throws IOException {
        mockServer.shutdown();
    }

    // ── queryOrderByClientOrderId ─────────────────────────────────────────

    @Test
    void queryOrderByClientOrderId_shouldReturnMappedDto_whenOrderExists() throws Exception {
        mockServer.enqueue(orderResponse("123", "my-order-1", "BTCUSDT", "BUY", "FILLED",
                "0.001", "0.001", "95000.00000000", "95000.00000000"));

        Optional<OrderDataDto> result = adapter.queryOrderByClientOrderId("BTCUSDT", "my-order-1");

        assertTrue(result.isPresent());
        OrderDataDto dto = result.get();
        assertEquals("123", dto.orderId());
        assertEquals("my-order-1", dto.clientOrderId());
        assertEquals("BTCUSDT", dto.symbol().value());
        assertEquals(OrderDataDto.OrderSide.BUY, dto.side());
        assertEquals(OrderDataDto.OrderStatus.FILLED, dto.status());
        assertEquals(new BigDecimal("0.001"), dto.executedQuantity());
        assertNotNull(dto.timestamp());

        RecordedRequest req = mockServer.takeRequest(1, TimeUnit.SECONDS);
        assertNotNull(req);
        assertEquals("GET", req.getMethod());
        assertTrue(req.getPath().contains("/api/v3/order"));
        assertTrue(req.getPath().contains("origClientOrderId=my-order-1"));
        assertTrue(req.getPath().contains("signature="));
        assertEquals("test-api-key", req.getHeader("X-MBX-APIKEY"));
    }

    @Test
    void queryOrderByClientOrderId_shouldReturnEmpty_whenHttp404() throws Exception {
        mockServer.enqueue(new MockResponse().setResponseCode(404).setBody("{\"code\":-2013,\"msg\":\"Order does not exist.\"}"));

        Optional<OrderDataDto> result = adapter.queryOrderByClientOrderId("BTCUSDT", "unknown-order");

        assertFalse(result.isPresent());
    }

    @Test
    void queryOrderByClientOrderId_shouldReturnEmpty_whenHttp400WithCode2013() throws Exception {
        // Binance returns 400 + code -2013 for missing/archived orders in some scenarios
        mockServer.enqueue(new MockResponse().setResponseCode(400)
                .setBody("{\"code\":-2013,\"msg\":\"Order does not exist.\"}"));

        Optional<OrderDataDto> result = adapter.queryOrderByClientOrderId("BTCUSDT", "archived-order");

        assertFalse(result.isPresent());
    }

    // ── submitOrder ───────────────────────────────────────────────────────

    @Test
    void submitOrder_shouldReturnDtoWithExchangeOrderId_whenSuccessful() throws Exception {
        mockServer.enqueue(orderResponse("999", "client-xyz", "ETHUSDT", "SELL", "NEW",
                "1.0", "0.0", "3000.00000000", "0.00000000"));

        SendOrderRequest request = new SendOrderRequest("BINANCE", "ETHUSDT",
                new BigDecimal("1.0"), new BigDecimal("3000"), OrderType.LIMIT,
                OrderSide.SELL, "client-xyz");

        OrderDataDto result = adapter.submitOrder(request);

        assertEquals("999", result.orderId());
        assertEquals("client-xyz", result.clientOrderId());
        assertEquals(OrderDataDto.OrderStatus.NEW, result.status());

        RecordedRequest req = mockServer.takeRequest(1, TimeUnit.SECONDS);
        assertNotNull(req);
        assertEquals("POST", req.getMethod());
        assertTrue(req.getPath().startsWith("/api/v3/order"));
        String body = req.getBody().readUtf8();
        assertTrue(body.contains("symbol=ETHUSDT"));
        assertTrue(body.contains("side=SELL"));
        assertTrue(body.contains("newClientOrderId=client-xyz"));
        assertTrue(body.contains("signature="));
        assertEquals("test-api-key", req.getHeader("X-MBX-APIKEY"));
    }

    @Test
    void submitOrder_shouldThrowWithBinanceCode_whenHttp400() {
        mockServer.enqueue(new MockResponse().setResponseCode(400)
                .setBody("{\"code\":-1013,\"msg\":\"Filter failure: MIN_NOTIONAL\"}"));

        SendOrderRequest request = new SendOrderRequest("BINANCE", "BTCUSDT",
                new BigDecimal("0.00001"), BigDecimal.ZERO, OrderType.MARKET,
                OrderSide.BUY, "client-bad");

        ExchangeQueryException ex = assertThrows(ExchangeQueryException.class,
                () -> adapter.submitOrder(request));
        assertEquals(ExchangeQueryException.ErrorType.INVALID_REQUEST, ex.errorType());
        assertTrue(ex.getMessage().contains("MIN_NOTIONAL") || ex.getMessage().contains("400"));
    }

    // ── queryAccountSnapshot ──────────────────────────────────────────────

    @Test
    void queryAccountSnapshot_shouldReturnMappedBalances() throws Exception {
        mockServer.enqueue(accountResponse());

        AccountDataDto result = adapter.queryAccountSnapshot();

        assertNotNull(result);
        assertNotNull(result.balances());
        assertTrue(result.balances().containsKey("BTC"));
        assertTrue(result.balances().containsKey("USDT"));
        assertTrue(result.balances().get("BTC").compareTo(BigDecimal.ZERO) > 0);
        assertNotNull(result.lastUpdateTime());

        RecordedRequest req = mockServer.takeRequest(1, TimeUnit.SECONDS);
        assertEquals("GET", req.getMethod());
        assertTrue(req.getPath().contains("/api/v3/account"));
        assertTrue(req.getPath().contains("signature="));
    }

    // ── listAllOpenOrders ─────────────────────────────────────────────────

    @Test
    void listAllOpenOrders_shouldReturnEmptyList_whenNoOpenOrders() throws Exception {
        mockServer.enqueue(new MockResponse().setBody("[]").setHeader("Content-Type", "application/json"));

        List<OrderDataDto> result = adapter.listAllOpenOrders();

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // ── rate limit ────────────────────────────────────────────────────────

    @Test
    void anyRequest_shouldThrowRateLimitException_whenHttp429() {
        mockServer.enqueue(new MockResponse().setResponseCode(429).setBody("{}"));

        ExchangeQueryException ex = assertThrows(ExchangeQueryException.class,
                () -> adapter.listAllOpenOrders());
        assertEquals(ExchangeQueryException.ErrorType.RATE_LIMIT, ex.errorType());
        assertTrue(ex.isRetryable());
    }

    // ── transient failure ─────────────────────────────────────────────────

    @Test
    void anyRequest_shouldThrowTransientException_whenHttp500() {
        mockServer.enqueue(new MockResponse().setResponseCode(500).setBody("Internal Server Error"));

        ExchangeQueryException ex = assertThrows(ExchangeQueryException.class,
                () -> adapter.listAllOpenOrders());
        assertEquals(ExchangeQueryException.ErrorType.TEMPORARY, ex.errorType());
        assertTrue(ex.isRetryable());
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private MockResponse orderResponse(String orderId, String clientOrderId, String symbol,
                                       String side, String status, String origQty,
                                       String executedQty, String price, String cummulativeQuoteQty) {
        String body = """
                {
                  "orderId": %s,
                  "clientOrderId": "%s",
                  "symbol": "%s",
                  "side": "%s",
                  "type": "LIMIT",
                  "status": "%s",
                  "origQty": "%s",
                  "executedQty": "%s",
                  "price": "%s",
                  "cummulativeQuoteQty": "%s",
                  "time": 1700000000000
                }
                """.formatted(orderId, clientOrderId, symbol, side, status, origQty, executedQty,
                price, cummulativeQuoteQty);
        return new MockResponse().setBody(body).setHeader("Content-Type", "application/json");
    }

    private MockResponse accountResponse() {
        String body = """
                {
                  "updateTime": 1700000000000,
                  "balances": [
                    { "asset": "BTC",  "free": "0.5",      "locked": "0.0" },
                    { "asset": "USDT", "free": "10000.00", "locked": "500.00" },
                    { "asset": "ETH",  "free": "0.0",      "locked": "0.0" }
                  ]
                }
                """;
        return new MockResponse().setBody(body).setHeader("Content-Type", "application/json");
    }
}
