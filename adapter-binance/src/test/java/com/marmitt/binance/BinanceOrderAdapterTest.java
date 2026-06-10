package com.marmitt.binance;

import com.marmitt.binance.filters.OrderFilterViolationException;
import com.marmitt.core.domain.Symbol;
import com.marmitt.core.dto.exchange.OrderSubmissionResult;
import com.marmitt.core.dto.websocket.data.OrderDataDto;
import com.marmitt.core.dto.websocket.request.MessageRequest;
import com.marmitt.core.dto.websocket.request.SendOrderRequest;
import com.marmitt.core.dto.websocket.request.StreamSubscriptionRequest;
import com.marmitt.core.dto.websocket.MessageContext;
import com.marmitt.core.dto.processing.ProcessingResult;
import com.marmitt.core.dto.websocket.data.ProcessorResponse;
import com.marmitt.core.dto.websocket.request.SendCancelOrderRequest;
import com.marmitt.core.enums.OrderSide;
import com.marmitt.core.enums.OrderType;
import com.marmitt.core.ports.outbound.exchange.rest.ExchangeOrderExecutionPort;
import com.marmitt.core.ports.outbound.exchange.streaming.ExchangeStreamingPort;
import com.marmitt.core.ports.outbound.websocket.WebSocketPort;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BinanceOrderAdapterTest {

    private static final String EXCHANGE = "BINANCE";
    private static final String SYMBOL = "BTCUSDT";
    private static final BigDecimal QTY = new BigDecimal("0.001");
    private static final BigDecimal PRICE = new BigDecimal("95000");

    private SendOrderRequest request(String clientOrderId) {
        return new SendOrderRequest(EXCHANGE, SYMBOL, QTY, PRICE, OrderType.LIMIT, OrderSide.BUY, clientOrderId);
    }

    private OrderDataDto orderDto(String clientOrderId, OrderDataDto.OrderStatus status) {
        return new OrderDataDto("123", clientOrderId, Symbol.of(SYMBOL),
                OrderDataDto.OrderSide.BUY, OrderDataDto.OrderType.LIMIT,
                QTY, BigDecimal.ZERO, PRICE, BigDecimal.ZERO, BigDecimal.ZERO,
                status, null, Instant.now());
    }

    @Test
    void submitOrder_usesWebSocketApi_whenUserStreamConnected() {
        RecordingWebSocketPort userStreamWs = new RecordingWebSocketPort(true);
        RecordingWebSocketPort marketWs = new RecordingWebSocketPort(true);
        StubStreamingPort streaming = new StubStreamingPort();
        StubRestPort rest = new StubRestPort(null);

        BinanceOrderAdapter adapter = new BinanceOrderAdapter(userStreamWs, marketWs, streaming, rest);
        OrderSubmissionResult result = adapter.submitOrder(request("t01"));

        assertTrue(result.asyncDispatched(), "should return Dispatched when sent via WS API");
        assertEquals(1, userStreamWs.sentMessages.size(), "user stream must receive the message");
        assertTrue(marketWs.sentMessages.isEmpty(), "market stream must not receive message");
        assertFalse(rest.called, "REST must not be called");
    }

    @Test
    void submitOrder_fallsBackToRest_whenUserStreamDisconnected() {
        RecordingWebSocketPort userStreamWs = new RecordingWebSocketPort(false);
        RecordingWebSocketPort marketWs = new RecordingWebSocketPort(true);
        StubStreamingPort streaming = new StubStreamingPort();
        StubRestPort rest = new StubRestPort(orderDto("t01", OrderDataDto.OrderStatus.NEW));

        BinanceOrderAdapter adapter = new BinanceOrderAdapter(userStreamWs, marketWs, streaming, rest);
        OrderSubmissionResult result = adapter.submitOrder(request("t01"));

        assertTrue(result.isCompleted(), "should return Completed when REST succeeds");
        assertEquals(OrderDataDto.OrderStatus.NEW, result.syncResult().status());
        assertTrue(rest.called, "REST must be called on user stream unavailability");
        assertTrue(userStreamWs.sentMessages.isEmpty());
    }

    @Test
    void submitOrder_fallsBackToStreaming_whenRestUnsupported() {
        RecordingWebSocketPort userStreamWs = new RecordingWebSocketPort(false);
        RecordingWebSocketPort marketWs = new RecordingWebSocketPort(true);
        StubStreamingPort streaming = new StubStreamingPort();
        StubRestPort rest = new StubRestPort(null) {
            @Override public OrderDataDto submitOrder(SendOrderRequest r) { throw new UnsupportedOperationException(); }
        };

        BinanceOrderAdapter adapter = new BinanceOrderAdapter(userStreamWs, marketWs, streaming, rest);
        OrderSubmissionResult result = adapter.submitOrder(request("t01"));

        assertTrue(result.asyncDispatched(), "should return Dispatched when sent via market stream");
        assertEquals(1, marketWs.sentMessages.size(), "market stream must receive the message");
    }

    @Test
    void submitOrder_returnsFailed_whenFilterViolationThrown() {
        RecordingWebSocketPort userStreamWs = new RecordingWebSocketPort(true);
        RecordingWebSocketPort marketWs = new RecordingWebSocketPort(true);
        StubStreamingPort streaming = new StubStreamingPort() {
            @Override public String formatMessage(MessageRequest r) {
                throw new OrderFilterViolationException("stepSize violation");
            }
        };
        StubRestPort rest = new StubRestPort(null);

        BinanceOrderAdapter adapter = new BinanceOrderAdapter(userStreamWs, marketWs, streaming, rest);
        OrderSubmissionResult result = adapter.submitOrder(request("t01"));

        assertTrue(result.isFailed(), "should return Failed on filter violation");
        assertEquals("stepSize violation", result.failureReason());
    }

    @Test
    void submitOrder_normalizesMissingClientOrderId_whenRestReturnsBlanckId() {
        RecordingWebSocketPort userStreamWs = new RecordingWebSocketPort(false);
        RecordingWebSocketPort marketWs = new RecordingWebSocketPort(true);
        StubStreamingPort streaming = new StubStreamingPort();
        OrderDataDto responseWithBlankId = new OrderDataDto("123", "", Symbol.of(SYMBOL),
                OrderDataDto.OrderSide.BUY, OrderDataDto.OrderType.LIMIT,
                QTY, BigDecimal.ZERO, PRICE, BigDecimal.ZERO, BigDecimal.ZERO,
                OrderDataDto.OrderStatus.NEW, null, Instant.now());
        StubRestPort rest = new StubRestPort(responseWithBlankId);

        BinanceOrderAdapter adapter = new BinanceOrderAdapter(userStreamWs, marketWs, streaming, rest);
        OrderSubmissionResult result = adapter.submitOrder(request("my-client-id"));

        assertTrue(result.isCompleted());
        assertEquals("my-client-id", result.syncResult().clientOrderId(),
                "missing clientOrderId from REST must be filled from the request");
    }

    // ---- stubs ----

    static class RecordingWebSocketPort implements WebSocketPort {
        final List<String> sentMessages = new ArrayList<>();
        private final boolean connected;
        RecordingWebSocketPort(boolean connected) { this.connected = connected; }
        @Override public void connect(String url, String name, UUID id) {}
        @Override public void disconnect(String name, UUID id) {}
        @Override public void sendMessage(String message) { sentMessages.add(message); }
        @Override public boolean isConnected() { return connected; }
    }

    static class StubStreamingPort implements ExchangeStreamingPort {
        @Override public String getExchangeName() { return EXCHANGE; }
        @Override public boolean requiresPostConnection() { return false; }
        @Override public String buildConnectionUrl(StreamSubscriptionRequest p, String n) { return ""; }
        @Override public String formatMessage(MessageRequest request) { return "{\"method\":\"order.place\"}"; }
        @Override public ProcessingResult<? extends ProcessorResponse> processMessage(String raw, MessageContext ctx) { return null; }
    }

    static class StubRestPort implements ExchangeOrderExecutionPort {
        private final OrderDataDto response;
        boolean called = false;
        StubRestPort(OrderDataDto response) { this.response = response; }
        @Override public OrderDataDto submitOrder(SendOrderRequest request) { called = true; return response; }
        @Override public OrderDataDto cancelOrder(SendCancelOrderRequest request) { throw new UnsupportedOperationException(); }
    }
}
