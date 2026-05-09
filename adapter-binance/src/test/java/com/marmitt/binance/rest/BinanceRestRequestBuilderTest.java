package com.marmitt.binance.rest;

import com.marmitt.binance.auth.BinanceCredentials;
import com.marmitt.binance.auth.BinanceRequestSigner;
import com.marmitt.core.dto.websocket.request.SendCancelOrderRequest;
import com.marmitt.core.dto.websocket.request.SendOrderRequest;
import com.marmitt.core.enums.OrderSide;
import com.marmitt.core.enums.OrderType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class BinanceRestRequestBuilderTest {

    private static final String BASE_URL = "https://testnet.binance.vision";
    private static final String API_KEY  = "test-api-key";

    BinanceRestRequestBuilder builder;

    @BeforeEach
    void setUp() {
        var credentials = new BinanceCredentials(API_KEY, "test-secret");
        var signer      = new BinanceRequestSigner(credentials);
        builder = new BinanceRestRequestBuilder(BASE_URL, signer);
    }

    // ── queryOrderByClientOrderId ─────────────────────────────────────────

    @Test
    void buildQueryOrderByClientOrderId_shouldProduceSignedGetRequest() {
        RestRequest req = builder.buildQueryOrderByClientOrderId("BTCUSDT", "my-order-1");

        assertEquals("GET", req.method());
        assertTrue(req.url().startsWith(BASE_URL + "/api/v3/order?"));
        assertTrue(req.url().contains("symbol=BTCUSDT"));
        assertTrue(req.url().contains("origClientOrderId=my-order-1"));
        assertTrue(req.url().contains("timestamp="));
        assertTrue(req.url().contains("signature="));
        assertEquals(API_KEY, req.headers().get("X-MBX-APIKEY"));
        assertNull(req.body());
    }

    // ── queryOrderByExchangeOrderId ───────────────────────────────────────

    @Test
    void buildQueryOrderByExchangeOrderId_shouldContainOrderId() {
        RestRequest req = builder.buildQueryOrderByExchangeOrderId("ETHUSDT", "98765");

        assertEquals("GET", req.method());
        assertTrue(req.url().contains("orderId=98765"));
        assertTrue(req.url().contains("symbol=ETHUSDT"));
        assertTrue(req.url().contains("signature="));
    }

    // ── submitOrder ───────────────────────────────────────────────────────

    @Test
    void buildSubmitOrder_limitOrder_shouldProduceSignedPostWithBody() {
        var request = new SendOrderRequest("BINANCE", "BTCUSDT",
                new BigDecimal("0.001"), new BigDecimal("95000"),
                OrderType.LIMIT, OrderSide.BUY, "client-xyz");

        RestRequest req = builder.buildSubmitOrder(request);

        assertEquals("POST", req.method());
        assertEquals(BASE_URL + "/api/v3/order", req.url());
        assertNotNull(req.body());
        assertTrue(req.body().contains("symbol=BTCUSDT"));
        assertTrue(req.body().contains("side=BUY"));
        assertTrue(req.body().contains("type=LIMIT"));
        assertTrue(req.body().contains("quantity=0.001"));
        assertTrue(req.body().contains("newClientOrderId=client-xyz"));
        assertTrue(req.body().contains("price=95000"));
        assertTrue(req.body().contains("timeInForce=GTC"));
        assertTrue(req.body().contains("signature="));
        assertEquals(API_KEY, req.headers().get("X-MBX-APIKEY"));
    }

    @Test
    void buildSubmitOrder_marketOrder_shouldOmitPriceAndTimeInForce() {
        var request = new SendOrderRequest("BINANCE", "BTCUSDT",
                new BigDecimal("0.001"), BigDecimal.ZERO,
                OrderType.MARKET, OrderSide.BUY, "client-mkt");

        RestRequest req = builder.buildSubmitOrder(request);

        assertFalse(req.body().contains("price="));
        assertFalse(req.body().contains("timeInForce="));
        assertTrue(req.body().contains("type=MARKET"));
    }

    // ── cancelOrder ───────────────────────────────────────────────────────

    @Test
    void buildCancelOrder_shouldProduceSignedDeleteRequest() {
        var request = new SendCancelOrderRequest("BINANCE", "client-abc", "BTCUSDT");

        RestRequest req = builder.buildCancelOrder(request);

        assertEquals("DELETE", req.method());
        assertTrue(req.url().startsWith(BASE_URL + "/api/v3/order?"));
        assertTrue(req.url().contains("origClientOrderId=client-abc"));
        assertTrue(req.url().contains("symbol=BTCUSDT"));
        assertTrue(req.url().contains("signature="));
        assertNull(req.body());
    }

    // ── listOpenOrders ────────────────────────────────────────────────────

    @Test
    void buildListOpenOrdersBySymbol_shouldContainSymbol() {
        RestRequest req = builder.buildListOpenOrdersBySymbol("ETHUSDT");

        assertEquals("GET", req.method());
        assertTrue(req.url().contains("/api/v3/openOrders"));
        assertTrue(req.url().contains("symbol=ETHUSDT"));
        assertTrue(req.url().contains("signature="));
    }

    @Test
    void buildListAllOpenOrders_shouldNotContainSymbol() {
        RestRequest req = builder.buildListAllOpenOrders();

        assertEquals("GET", req.method());
        assertTrue(req.url().contains("/api/v3/openOrders"));
        assertFalse(req.url().contains("symbol="));
        assertTrue(req.url().contains("signature="));
    }

    // ── accountSnapshot ───────────────────────────────────────────────────

    @Test
    void buildAccountSnapshot_shouldPointToAccountEndpoint() {
        RestRequest req = builder.buildAccountSnapshot();

        assertEquals("GET", req.method());
        assertTrue(req.url().startsWith(BASE_URL + "/api/v3/account?"));
        assertTrue(req.url().contains("signature="));
        assertEquals(API_KEY, req.headers().get("X-MBX-APIKEY"));
    }

    // ── signature uniqueness ──────────────────────────────────────────────

    @Test
    void eachBuild_shouldProduceUniqueSignature_dueToTimestamp() throws InterruptedException {
        RestRequest req1 = builder.buildAccountSnapshot();
        Thread.sleep(2);
        RestRequest req2 = builder.buildAccountSnapshot();

        assertNotEquals(req1.url(), req2.url(), "timestamps differ so signatures must differ");
    }
}
