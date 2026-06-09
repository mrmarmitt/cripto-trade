package com.marmitt.application.spring.infrastructure.exchange;

import com.marmitt.core.domain.Symbol;
import com.marmitt.core.domain.runner.ClientOrderId;
import com.marmitt.core.dto.processing.ProcessingResult;
import com.marmitt.core.dto.runner.OrderDispatchCommand;
import com.marmitt.core.dto.websocket.MessageContext;
import com.marmitt.core.dto.websocket.data.OrderDataDto;
import com.marmitt.core.dto.websocket.data.ProcessorResponse;
import com.marmitt.core.dto.websocket.request.MessageRequest;
import com.marmitt.core.dto.websocket.request.SendCancelOrderRequest;
import com.marmitt.core.dto.websocket.request.SendOrderRequest;
import com.marmitt.core.dto.websocket.request.StreamSubscriptionRequest;
import com.marmitt.core.enums.TransactionType;
import com.marmitt.core.ports.inbound.runner.OrderConciliationPort;
import com.marmitt.core.ports.outbound.exchange.rest.ExchangeAccountQueryPort;
import com.marmitt.core.ports.outbound.exchange.rest.ExchangeBootReadinessPort;
import com.marmitt.core.ports.outbound.exchange.rest.ExchangeOrderExecutionPort;
import com.marmitt.core.ports.outbound.exchange.rest.ExchangeOrderQueryPort;
import com.marmitt.core.ports.outbound.exchange.streaming.ExchangeStreamingPort;
import com.marmitt.core.ports.outbound.exchange.streaming.ExchangeUserStreamPort;
import com.marmitt.core.ports.outbound.exchange.streaming.UserStreamSession;
import com.marmitt.core.ports.outbound.exchange.streaming.UserStreamSessionPort;
import com.marmitt.core.ports.outbound.repository.ExchangeAdapterRepositoryPort;
import com.marmitt.core.ports.outbound.websocket.WebSocketPort;
import com.marmitt.core.ports.outbound.websocket.WebSocketPortRegistryPort;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class OrderDispatchAdapterTest {

    private static final String EXCHANGE = "BINANCE";
    private static final String SYMBOL = "BTCUSDT";
    private static final BigDecimal QTY = new BigDecimal("0.001");
    private static final BigDecimal PRICE = new BigDecimal("95000");

    private OrderDispatchCommand command() {
        return new OrderDispatchCommand(
                ClientOrderId.generate("t01", TransactionType.BUY),
                UUID.randomUUID(), SYMBOL, EXCHANGE, TransactionType.BUY, QTY, PRICE);
    }

    @Test
    void dispatch_usesWebSocketApi_whenUserStreamAvailable() {
        RecordingWebSocketPort wsApiPort = new RecordingWebSocketPort();
        RecordingConciliationPort conciliation = new RecordingConciliationPort();
        StubStreamingPort streaming = new StubStreamingPort();

        WebSocketPortRegistryPort registry = new StubWebSocketRegistry(wsApiPort, null);
        ExchangeAdapterRepositoryPort adapterRepo = new StubAdapterRepository(streaming, null);

        OrderDispatchAdapter adapter = new OrderDispatchAdapter(adapterRepo, conciliation, registry);
        adapter.dispatch(command());

        assertEquals(1, wsApiPort.sentMessages.size(), "WS API must have received one message");
        assertTrue(conciliation.received.isEmpty(), "conciliation must not be called when dispatching via WS API");
    }

    @Test
    void dispatch_fallsBackToRest_whenNoUserStream() {
        RecordingConciliationPort conciliation = new RecordingConciliationPort();
        StubStreamingPort streaming = new StubStreamingPort();
        StubOrderExecutionPort restPort = new StubOrderExecutionPort(OrderDataDto.OrderStatus.NEW);

        WebSocketPortRegistryPort registry = new StubWebSocketRegistry(null, null);
        ExchangeAdapterRepositoryPort adapterRepo = new StubAdapterRepository(streaming, restPort);

        OrderDispatchAdapter adapter = new OrderDispatchAdapter(adapterRepo, conciliation, registry);
        adapter.dispatch(command());

        assertEquals(1, conciliation.received.size(), "REST path must trigger conciliation");
        assertEquals(OrderDataDto.OrderStatus.NEW, conciliation.received.get(0).status());
    }

    @Test
    void dispatch_rest_propagatesRejectedStatus() {
        RecordingConciliationPort conciliation = new RecordingConciliationPort();
        StubStreamingPort streaming = new StubStreamingPort();
        StubOrderExecutionPort restPort = new StubOrderExecutionPort(OrderDataDto.OrderStatus.REJECTED);

        WebSocketPortRegistryPort registry = new StubWebSocketRegistry(null, null);
        ExchangeAdapterRepositoryPort adapterRepo = new StubAdapterRepository(streaming, restPort);

        OrderDispatchAdapter adapter = new OrderDispatchAdapter(adapterRepo, conciliation, registry);
        adapter.dispatch(command());

        assertEquals(1, conciliation.received.size());
        assertEquals(OrderDataDto.OrderStatus.REJECTED, conciliation.received.get(0).status());
    }

    @Test
    void dispatch_rejectsOrder_whenDispatchBlocked() {
        RecordingWebSocketPort wsApiPort = new RecordingWebSocketPort();
        RecordingConciliationPort conciliation = new RecordingConciliationPort();
        StubStreamingPort streaming = new StubStreamingPort();
        StubOrderExecutionPort restPort = new StubOrderExecutionPort(OrderDataDto.OrderStatus.NEW);

        WebSocketPortRegistryPort registry = new StubWebSocketRegistry(wsApiPort, null);
        StubAdapterRepository adapterRepo = new StubAdapterRepository(streaming, restPort) {
            @Override public boolean isDispatchBlocked(String exchangeName) { return true; }
        };

        OrderDispatchAdapter adapter = new OrderDispatchAdapter(adapterRepo, conciliation, registry);
        adapter.dispatch(command());

        assertTrue(wsApiPort.sentMessages.isEmpty(), "blocked dispatch must not send via WS API");
        assertEquals(1, conciliation.received.size(), "blocked dispatch must conciliate as REJECTED");
        assertEquals(OrderDataDto.OrderStatus.REJECTED, conciliation.received.get(0).status());
    }

    @Test
    void dispatch_fallsBackToRest_whenUserStreamNotConnected() {
        RecordingWebSocketPort disconnectedWsApiPort = new RecordingWebSocketPort() {
            @Override public boolean isConnected() { return false; }
        };
        RecordingConciliationPort conciliation = new RecordingConciliationPort();
        StubStreamingPort streaming = new StubStreamingPort();
        StubOrderExecutionPort restPort = new StubOrderExecutionPort(OrderDataDto.OrderStatus.NEW);

        WebSocketPortRegistryPort registry = new StubWebSocketRegistry(disconnectedWsApiPort, null);
        ExchangeAdapterRepositoryPort adapterRepo = new StubAdapterRepository(streaming, restPort);

        OrderDispatchAdapter adapter = new OrderDispatchAdapter(adapterRepo, conciliation, registry);
        adapter.dispatch(command());

        assertTrue(disconnectedWsApiPort.sentMessages.isEmpty(), "disconnected WS API must not receive messages");
        assertEquals(1, conciliation.received.size(), "must fall back to REST conciliation");
    }

    @Test
    void dispatch_fallsBackToStreaming_whenNoUserStreamAndNoRest() {
        RecordingWebSocketPort marketStreamPort = new RecordingWebSocketPort();
        StubStreamingPort streaming = new StubStreamingPort();

        WebSocketPortRegistryPort registry = new StubWebSocketRegistry(null, marketStreamPort);
        ExchangeAdapterRepositoryPort adapterRepo = new StubAdapterRepository(streaming, null);

        RecordingConciliationPort conciliation = new RecordingConciliationPort();
        OrderDispatchAdapter adapter = new OrderDispatchAdapter(adapterRepo, conciliation, registry);
        adapter.dispatch(command());

        assertEquals(1, marketStreamPort.sentMessages.size(), "streaming must receive one message");
        assertTrue(conciliation.received.isEmpty(), "conciliation must not be called on streaming path");
    }

    // ---- stubs ----

    static class RecordingWebSocketPort implements WebSocketPort {
        final List<String> sentMessages = new ArrayList<>();
        @Override public void connect(String url, String exchangeName, UUID connectionId) {}
        @Override public void disconnect(String exchangeName, UUID id) {}
        @Override public void sendMessage(String message) { sentMessages.add(message); }
        @Override public boolean isConnected() { return true; }
    }

    static class RecordingConciliationPort implements OrderConciliationPort {
        final List<OrderDataDto> received = new ArrayList<>();
        @Override public void execute(OrderDataDto orderData) { received.add(orderData); }
    }

    static class StubStreamingPort implements ExchangeStreamingPort {
        @Override public String getExchangeName() { return EXCHANGE; }
        @Override public boolean requiresPostConnection() { return false; }
        @Override public String buildConnectionUrl(StreamSubscriptionRequest p, String n) { return ""; }
        @Override public String formatMessage(MessageRequest request) { return "{\"method\":\"order.place\"}"; }
        @Override public ProcessingResult<? extends ProcessorResponse> processMessage(String raw, MessageContext ctx) { return null; }
    }

    static class StubOrderExecutionPort implements ExchangeOrderExecutionPort {
        private final OrderDataDto.OrderStatus status;
        StubOrderExecutionPort(OrderDataDto.OrderStatus status) { this.status = status; }
        @Override
        public OrderDataDto submitOrder(SendOrderRequest request) {
            return new OrderDataDto("123", request.getClientOrderId(), Symbol.of(SYMBOL),
                    OrderDataDto.OrderSide.BUY, OrderDataDto.OrderType.LIMIT,
                    QTY, BigDecimal.ZERO, PRICE, BigDecimal.ZERO, BigDecimal.ZERO,
                    status, null, Instant.now());
        }
        @Override public OrderDataDto cancelOrder(SendCancelOrderRequest request) { throw new UnsupportedOperationException(); }
    }

    static class StubWebSocketRegistry implements WebSocketPortRegistryPort {
        private final WebSocketPort userStream;
        private final WebSocketPort marketStream;
        StubWebSocketRegistry(WebSocketPort userStream, WebSocketPort marketStream) {
            this.userStream = userStream;
            this.marketStream = marketStream;
        }
        @Override public void register(String exchangeName, WebSocketPort port) {}
        @Override public Optional<WebSocketPort> findByExchangeName(String exchangeName) { return Optional.ofNullable(marketStream); }
        @Override public void registerUserStream(String exchangeName, WebSocketPort port) {}
        @Override public Optional<WebSocketPort> findUserStreamByExchangeName(String exchangeName) { return Optional.ofNullable(userStream); }
    }

    static class StubAdapterRepository implements ExchangeAdapterRepositoryPort {
        private final ExchangeStreamingPort streaming;
        private final ExchangeOrderExecutionPort orderExecution;
        StubAdapterRepository(ExchangeStreamingPort streaming, ExchangeOrderExecutionPort orderExecution) {
            this.streaming = streaming;
            this.orderExecution = orderExecution;
        }
        @Override public Optional<ExchangeStreamingPort> findStreamingByName(String n) { return Optional.ofNullable(streaming); }
        @Override public Optional<ExchangeOrderExecutionPort> findOrderExecutionByName(String n) { return Optional.ofNullable(orderExecution); }
        @Override public void registerStreamingAdapter(ExchangeStreamingPort a) {}
        @Override public void registerUserStreamAdapter(ExchangeUserStreamPort a) {}
        @Override public void registerUserStreamSession(UserStreamSessionPort s) {}
        @Override public void storeActiveSession(UUID id, UserStreamSession s) {}
        @Override public void registerOrderExecutionAdapter(String n, ExchangeOrderExecutionPort a) {}
        @Override public void registerOrderQueryAdapter(String n, ExchangeOrderQueryPort a) {}
        @Override public void registerAccountQueryAdapter(String n, ExchangeAccountQueryPort a) {}
        @Override public void registerBootReadinessAdapter(String n, ExchangeBootReadinessPort a) {}
        @Override public void registerPortfolioByAdapter(String n, UUID id) {}
        @Override public boolean hasAdapter(String n) { return true; }
        @Override public Set<String> getAllExchangeNames() { return Set.of(EXCHANGE); }
        @Override public int getAdapterCount() { return 1; }
        @Override public Optional<ExchangeOrderQueryPort> findOrderQueryByName(String n) { return Optional.empty(); }
        @Override public Optional<ExchangeAccountQueryPort> findAccountQueryByName(String n) { return Optional.empty(); }
        @Override public Optional<ExchangeBootReadinessPort> findBootReadinessByName(String n) { return Optional.empty(); }
        @Override public Optional<ExchangeUserStreamPort> findUserStreamByName(String n) { return Optional.empty(); }
        @Override public Optional<UserStreamSessionPort> findUserStreamSessionByName(String n) { return Optional.empty(); }
        @Override public Optional<UserStreamSession> findActiveSession(UUID id) { return Optional.empty(); }
        @Override public void removeActiveSession(UUID id) {}
        @Override public void blockDispatch(String exchangeName) {}
        @Override public void unblockDispatch(String exchangeName) {}
        @Override public boolean isDispatchBlocked(String exchangeName) { return false; }
    }
}
