package com.marmitt.application.spring.infrastructure.exchange;

import com.marmitt.core.domain.Symbol;
import com.marmitt.core.domain.runner.ClientOrderId;
import com.marmitt.core.dto.exchange.OrderSubmissionResult;
import com.marmitt.core.dto.runner.OrderCancelCommand;
import com.marmitt.core.dto.runner.OrderDispatchCommand;
import com.marmitt.core.dto.websocket.data.OrderDataDto;
import com.marmitt.core.dto.websocket.request.SendCancelOrderRequest;
import com.marmitt.core.dto.websocket.request.SendOrderRequest;
import com.marmitt.core.enums.TransactionType;
import com.marmitt.core.ports.inbound.runner.OrderConciliationPort;
import com.marmitt.core.ports.outbound.exchange.ExchangeAdapterDescriptor;
import com.marmitt.core.ports.outbound.exchange.ExchangeOrderPort;
import com.marmitt.core.ports.outbound.exchange.rest.ExchangeOrderExecutionPort;
import com.marmitt.core.ports.outbound.exchange.streaming.UserStreamSession;
import com.marmitt.core.ports.outbound.repository.ExchangeAdapterRepositoryPort;
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

    private OrderDataDto sampleDto(OrderDataDto.OrderStatus status) {
        return new OrderDataDto("123", "t01-BUY-001", Symbol.of(SYMBOL),
                OrderDataDto.OrderSide.BUY, OrderDataDto.OrderType.LIMIT,
                QTY, BigDecimal.ZERO, PRICE, BigDecimal.ZERO, BigDecimal.ZERO,
                status, null, Instant.now());
    }

    @Test
    void dispatch_rejectsOrder_whenDispatchBlocked() {
        RecordingConciliationPort conciliation = new RecordingConciliationPort();
        StubAdapterRepository adapterRepo = new StubAdapterRepository(new StubOrderPort(OrderSubmissionResult.dispatched())) {
            @Override public boolean isDispatchBlocked(String exchangeName) { return true; }
        };

        new OrderDispatchAdapter(adapterRepo, conciliation).dispatch(command());

        assertEquals(1, conciliation.received.size(), "blocked dispatch must conciliate as REJECTED");
        assertEquals(OrderDataDto.OrderStatus.REJECTED, conciliation.received.get(0).status());
    }

    @Test
    void dispatch_doesNotConciliate_whenPortReturnsDispatched() {
        RecordingConciliationPort conciliation = new RecordingConciliationPort();
        StubAdapterRepository adapterRepo = new StubAdapterRepository(
                new StubOrderPort(OrderSubmissionResult.dispatched()));

        new OrderDispatchAdapter(adapterRepo, conciliation).dispatch(command());

        assertTrue(conciliation.received.isEmpty(), "async dispatch must not trigger conciliation");
    }

    @Test
    void dispatch_conciliates_whenPortReturnsCompleted() {
        RecordingConciliationPort conciliation = new RecordingConciliationPort();
        OrderDataDto dto = sampleDto(OrderDataDto.OrderStatus.NEW);
        StubAdapterRepository adapterRepo = new StubAdapterRepository(
                new StubOrderPort(OrderSubmissionResult.completed(dto)));

        new OrderDispatchAdapter(adapterRepo, conciliation).dispatch(command());

        assertEquals(1, conciliation.received.size());
        assertEquals(OrderDataDto.OrderStatus.NEW, conciliation.received.get(0).status());
    }

    @Test
    void dispatch_conciliatesRejected_whenPortReturnsFailed() {
        RecordingConciliationPort conciliation = new RecordingConciliationPort();
        StubAdapterRepository adapterRepo = new StubAdapterRepository(
                new StubOrderPort(OrderSubmissionResult.failed("filter violation: stepSize")));

        new OrderDispatchAdapter(adapterRepo, conciliation).dispatch(command());

        assertEquals(1, conciliation.received.size());
        assertEquals(OrderDataDto.OrderStatus.REJECTED, conciliation.received.get(0).status());
        assertEquals("filter violation: stepSize", conciliation.received.get(0).rejectReason());
    }

    @Test
    void cancel_translatesToSendCancelOrderRequest_andInvokesOrderExecution() {
        RecordingOrderExecutionPort execution = new RecordingOrderExecutionPort();
        StubAdapterRepository adapterRepo = new StubAdapterRepository(
                new StubOrderPort(OrderSubmissionResult.dispatched()), execution);

        OrderCancelCommand command = new OrderCancelCommand("t01-BUY-001", UUID.randomUUID(), SYMBOL, EXCHANGE);
        new OrderDispatchAdapter(adapterRepo, new RecordingConciliationPort()).cancel(command);

        assertEquals(1, execution.canceled.size(), "cancel must reach the order execution transport");
        SendCancelOrderRequest sent = execution.canceled.get(0);
        assertEquals("t01-BUY-001", sent.getClientOrderId());
        assertEquals(SYMBOL, sent.getSymbol());
    }

    @Test
    void cancel_isSkipped_whenDispatchBlocked() {
        RecordingOrderExecutionPort execution = new RecordingOrderExecutionPort();
        StubAdapterRepository adapterRepo = new StubAdapterRepository(
                new StubOrderPort(OrderSubmissionResult.dispatched()), execution) {
            @Override public boolean isDispatchBlocked(String exchangeName) { return true; }
        };

        OrderCancelCommand command = new OrderCancelCommand("t01-BUY-001", UUID.randomUUID(), SYMBOL, EXCHANGE);
        new OrderDispatchAdapter(adapterRepo, new RecordingConciliationPort()).cancel(command);

        assertTrue(execution.canceled.isEmpty(), "blocked exchange must not receive a cancel");
    }

    @Test
    void cancel_isNoOp_whenCancelUnsupported() {
        ExchangeOrderExecutionPort throwing = new ExchangeOrderExecutionPort() {
            @Override public OrderDataDto submitOrder(SendOrderRequest request) { throw new UnsupportedOperationException(); }
            @Override public OrderDataDto cancelOrder(SendCancelOrderRequest request) { throw new UnsupportedOperationException(); }
        };
        StubAdapterRepository adapterRepo = new StubAdapterRepository(
                new StubOrderPort(OrderSubmissionResult.dispatched()), throwing);

        OrderCancelCommand command = new OrderCancelCommand("t01-BUY-001", UUID.randomUUID(), SYMBOL, EXCHANGE);
        // capability anunciada mas cancel nao suportado -> no-op logado, sem propagar excecao.
        assertDoesNotThrow(() -> new OrderDispatchAdapter(adapterRepo, new RecordingConciliationPort()).cancel(command));
    }

    @Test
    void cancel_isNoOp_whenOrderExecutionNotAvailable() {
        RecordingConciliationPort conciliation = new RecordingConciliationPort();
        StubAdapterRepository adapterRepo = new StubAdapterRepository(
                new StubOrderPort(OrderSubmissionResult.dispatched())); // sem orderExecution

        OrderCancelCommand command = new OrderCancelCommand("t01-BUY-001", UUID.randomUUID(), SYMBOL, EXCHANGE);
        // hasOrderExecution()=false -> no-op logado, sem excecao.
        new OrderDispatchAdapter(adapterRepo, conciliation).cancel(command);

        assertTrue(conciliation.received.isEmpty());
    }

    // ---- stubs ----

    static class RecordingConciliationPort implements OrderConciliationPort {
        final List<OrderDataDto> received = new ArrayList<>();
        @Override public void execute(OrderDataDto orderData) { received.add(orderData); }
    }

    static class RecordingOrderExecutionPort implements ExchangeOrderExecutionPort {
        final List<SendCancelOrderRequest> canceled = new ArrayList<>();
        @Override public OrderDataDto submitOrder(SendOrderRequest request) { throw new UnsupportedOperationException(); }
        @Override public OrderDataDto cancelOrder(SendCancelOrderRequest request) { canceled.add(request); return null; }
    }

    static class StubOrderPort implements ExchangeOrderPort {
        private final OrderSubmissionResult result;
        StubOrderPort(OrderSubmissionResult result) { this.result = result; }
        @Override public String getExchangeName() { return EXCHANGE; }
        @Override public OrderSubmissionResult submitOrder(SendOrderRequest request) { return result; }
    }

    static class StubDescriptor implements ExchangeAdapterDescriptor {
        private final ExchangeOrderPort orderPort;
        private final ExchangeOrderExecutionPort orderExecution;
        StubDescriptor(ExchangeOrderPort orderPort) { this(orderPort, null); }
        StubDescriptor(ExchangeOrderPort orderPort, ExchangeOrderExecutionPort orderExecution) {
            this.orderPort = orderPort;
            this.orderExecution = orderExecution;
        }
        @Override public String exchangeName() { return EXCHANGE; }
        @Override public com.marmitt.core.ports.outbound.exchange.streaming.ExchangeStreamingPort streaming() { return null; }
        @Override public ExchangeOrderPort orderPort() { return orderPort; }
        @Override public boolean hasUserStream() { return false; }
        @Override public com.marmitt.core.ports.outbound.exchange.streaming.ExchangeUserStreamPort userStream() { throw new com.marmitt.core.exceptions.UnsupportedCapabilityException(EXCHANGE, "userStream"); }
        @Override public boolean hasUserStreamSession() { return false; }
        @Override public com.marmitt.core.ports.outbound.exchange.streaming.UserStreamSessionPort userStreamSession() { throw new com.marmitt.core.exceptions.UnsupportedCapabilityException(EXCHANGE, "userStreamSession"); }
        @Override public boolean hasOrderExecution() { return orderExecution != null; }
        @Override public ExchangeOrderExecutionPort orderExecution() {
            if (orderExecution == null) { throw new com.marmitt.core.exceptions.UnsupportedCapabilityException(EXCHANGE, "orderExecution"); }
            return orderExecution;
        }
        @Override public boolean hasOrderQuery() { return false; }
        @Override public com.marmitt.core.ports.outbound.exchange.rest.ExchangeOrderQueryPort orderQuery() { throw new com.marmitt.core.exceptions.UnsupportedCapabilityException(EXCHANGE, "orderQuery"); }
        @Override public boolean hasAccountQuery() { return false; }
        @Override public com.marmitt.core.ports.outbound.exchange.rest.ExchangeAccountQueryPort accountQuery() { throw new com.marmitt.core.exceptions.UnsupportedCapabilityException(EXCHANGE, "accountQuery"); }
        @Override public boolean hasTradeHistory() { return false; }
        @Override public com.marmitt.core.ports.outbound.exchange.TradeHistoryQueryPort tradeHistory() { throw new com.marmitt.core.exceptions.UnsupportedCapabilityException(EXCHANGE, "tradeHistory"); }
        @Override public boolean hasBootReadiness() { return false; }
        @Override public com.marmitt.core.ports.outbound.exchange.rest.ExchangeBootReadinessPort bootReadiness() { throw new com.marmitt.core.exceptions.UnsupportedCapabilityException(EXCHANGE, "bootReadiness"); }
    }

    static class StubAdapterRepository implements ExchangeAdapterRepositoryPort {
        private final ExchangeAdapterDescriptor descriptor;
        StubAdapterRepository(ExchangeOrderPort orderPort) { this.descriptor = new StubDescriptor(orderPort); }
        StubAdapterRepository(ExchangeOrderPort orderPort, ExchangeOrderExecutionPort orderExecution) {
            this.descriptor = new StubDescriptor(orderPort, orderExecution);
        }
        @Override public Optional<ExchangeAdapterDescriptor> findAdapter(String n) { return Optional.of(descriptor); }
        @Override public boolean hasAdapter(String n) { return true; }
        @Override public Set<String> getAllExchangeNames() { return Set.of(EXCHANGE); }
        @Override public void storeActiveSession(UUID id, UserStreamSession s) {}
        @Override public Optional<UserStreamSession> findActiveSession(UUID id) { return Optional.empty(); }
        @Override public void removeActiveSession(UUID id) {}
        @Override public void blockDispatch(String exchangeName) {}
        @Override public void unblockDispatch(String exchangeName) {}
        @Override public boolean isDispatchBlocked(String exchangeName) { return false; }
        @Override public void registerPortfolioByAdapter(String n, UUID id) {}
    }
}
