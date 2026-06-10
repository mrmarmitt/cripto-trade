package com.marmitt.application.spring.infrastructure.exchange;

import com.marmitt.core.domain.Symbol;
import com.marmitt.core.domain.runner.ClientOrderId;
import com.marmitt.core.dto.exchange.OrderSubmissionResult;
import com.marmitt.core.dto.runner.OrderDispatchCommand;
import com.marmitt.core.dto.websocket.data.OrderDataDto;
import com.marmitt.core.dto.websocket.request.SendOrderRequest;
import com.marmitt.core.enums.TransactionType;
import com.marmitt.core.ports.inbound.runner.OrderConciliationPort;
import com.marmitt.core.ports.outbound.exchange.ExchangeOrderPort;
import com.marmitt.core.ports.outbound.exchange.rest.ExchangeAccountQueryPort;
import com.marmitt.core.ports.outbound.exchange.rest.ExchangeBootReadinessPort;
import com.marmitt.core.ports.outbound.exchange.rest.ExchangeOrderExecutionPort;
import com.marmitt.core.ports.outbound.exchange.rest.ExchangeOrderQueryPort;
import com.marmitt.core.ports.outbound.exchange.streaming.ExchangeStreamingPort;
import com.marmitt.core.ports.outbound.exchange.streaming.ExchangeUserStreamPort;
import com.marmitt.core.ports.outbound.exchange.streaming.UserStreamSession;
import com.marmitt.core.ports.outbound.exchange.streaming.UserStreamSessionPort;
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

    // ---- stubs ----

    static class RecordingConciliationPort implements OrderConciliationPort {
        final List<OrderDataDto> received = new ArrayList<>();
        @Override public void execute(OrderDataDto orderData) { received.add(orderData); }
    }

    static class StubOrderPort implements ExchangeOrderPort {
        private final OrderSubmissionResult result;
        StubOrderPort(OrderSubmissionResult result) { this.result = result; }
        @Override public String getExchangeName() { return EXCHANGE; }
        @Override public OrderSubmissionResult submitOrder(SendOrderRequest request) { return result; }
    }

    static class StubAdapterRepository implements ExchangeAdapterRepositoryPort {
        private final ExchangeOrderPort orderPort;
        StubAdapterRepository(ExchangeOrderPort orderPort) { this.orderPort = orderPort; }
        @Override public Optional<ExchangeOrderPort> findOrderPortByName(String n) { return Optional.ofNullable(orderPort); }
        @Override public void registerOrderPort(ExchangeOrderPort p) {}
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
        @Override public Optional<ExchangeStreamingPort> findStreamingByName(String n) { return Optional.empty(); }
        @Override public Optional<ExchangeOrderExecutionPort> findOrderExecutionByName(String n) { return Optional.empty(); }
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
