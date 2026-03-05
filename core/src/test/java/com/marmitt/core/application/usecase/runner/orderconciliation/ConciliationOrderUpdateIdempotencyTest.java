package com.marmitt.core.application.usecase.runner.orderconciliation;

import com.marmitt.core.domain.Symbol;
import com.marmitt.core.domain.runner.ClientOrderId;
import com.marmitt.core.domain.runner.Transaction;
import com.marmitt.core.dto.websocket.data.OrderDataDto;
import com.marmitt.core.enums.TransactionStatus;
import com.marmitt.core.enums.TransactionType;
import com.marmitt.core.ports.outbound.events.EventPublisherPort;
import com.marmitt.core.ports.outbound.repository.StrategyRunnerRepositoryPort;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ConciliationOrderUpdateIdempotencyTest {

    @Test
    void executeSkipsInvalidClientOrderId() {
        StrategyRunnerRepositoryPort repository = mock(StrategyRunnerRepositoryPort.class);
        EventPublisherPort publisher = mock(EventPublisherPort.class);
        ConciliationOrderUpdate conciliation = new ConciliationOrderUpdate(repository, publisher);

        OrderDataDto orderData = orderData(
                "invalid-client-order-id",
                OrderDataDto.OrderStatus.NEW,
                BigDecimal.ZERO,
                BigDecimal.ZERO
        );

        conciliation.execute(orderData, tx -> {}, (tx, od, finalFill) -> {}, tx -> {});

        verify(repository, never()).findTransactionByClientOrderId(orderData.clientOrderId());
    }

    @Test
    void executeNewTransitionsPendingTransactionAndCallsSubmitAction() {
        StrategyRunnerRepositoryPort repository = mock(StrategyRunnerRepositoryPort.class);
        EventPublisherPort publisher = mock(EventPublisherPort.class);
        ConciliationOrderUpdate conciliation = new ConciliationOrderUpdate(repository, publisher);

        Transaction transaction = newBuyTransaction();
        String clientOrderId = transaction.getClientOrderId();
        when(repository.findTransactionByClientOrderId(clientOrderId)).thenReturn(Optional.of(transaction));

        AtomicInteger submitCalls = new AtomicInteger();
        AtomicInteger fillCalls = new AtomicInteger();
        AtomicInteger releaseCalls = new AtomicInteger();

        conciliation.execute(
                orderData(clientOrderId, OrderDataDto.OrderStatus.NEW, BigDecimal.ZERO, BigDecimal.ZERO),
                tx -> submitCalls.incrementAndGet(),
                (tx, od, finalFill) -> fillCalls.incrementAndGet(),
                tx -> releaseCalls.incrementAndGet()
        );

        assertEquals(TransactionStatus.SUBMITTED, transaction.getStatus());
        assertEquals("EX_ORDER_1", transaction.getExchangeOrderId());
        assertEquals(1, submitCalls.get());
        assertEquals(0, fillCalls.get());
        assertEquals(0, releaseCalls.get());
    }

    @Test
    void executeNewIgnoresAlreadySubmittedTransaction() {
        StrategyRunnerRepositoryPort repository = mock(StrategyRunnerRepositoryPort.class);
        EventPublisherPort publisher = mock(EventPublisherPort.class);
        ConciliationOrderUpdate conciliation = new ConciliationOrderUpdate(repository, publisher);

        Transaction transaction = newBuyTransaction();
        transaction.submit("PREVIOUS_EX_ORDER");
        String clientOrderId = transaction.getClientOrderId();
        when(repository.findTransactionByClientOrderId(clientOrderId)).thenReturn(Optional.of(transaction));

        AtomicInteger submitCalls = new AtomicInteger();

        conciliation.execute(
                orderData(clientOrderId, OrderDataDto.OrderStatus.NEW, BigDecimal.ZERO, BigDecimal.ZERO),
                tx -> submitCalls.incrementAndGet(),
                (tx, od, finalFill) -> {
                },
                tx -> {
                }
        );

        assertEquals(TransactionStatus.SUBMITTED, transaction.getStatus());
        assertEquals("PREVIOUS_EX_ORDER", transaction.getExchangeOrderId());
        assertEquals(0, submitCalls.get());
    }

    @Test
    void processFillIgnoresDuplicatePartiallyFilledWhenQuantityDidNotIncrease() {
        StrategyRunnerRepositoryPort repository = mock(StrategyRunnerRepositoryPort.class);
        EventPublisherPort publisher = mock(EventPublisherPort.class);
        ConciliationOrderUpdate conciliation = new ConciliationOrderUpdate(repository, publisher);

        Transaction transaction = newBuyTransaction();
        transaction.submit("EX_ORDER_1");
        transaction.partialFill(new BigDecimal("1.00000000"), new BigDecimal("10.00000000"));

        conciliation.processFill(
                transaction,
                orderData(
                        transaction.getClientOrderId(),
                        OrderDataDto.OrderStatus.PARTIALLY_FILLED,
                        new BigDecimal("1.00000000"),
                        new BigDecimal("10.50000000")
                ),
                false
        );

        assertEquals(TransactionStatus.PARTIAL, transaction.getStatus());
        assertAmount("1.00000000", transaction.getExecutedQuantity());
        assertAmount("10.00000000", transaction.getExecutedPrice());
        verifyNoInteractions(repository, publisher);
    }

    @Test
    void processFillIgnoresDuplicateFilledWhenAlreadyFinal() {
        StrategyRunnerRepositoryPort repository = mock(StrategyRunnerRepositoryPort.class);
        EventPublisherPort publisher = mock(EventPublisherPort.class);
        ConciliationOrderUpdate conciliation = new ConciliationOrderUpdate(repository, publisher);

        Transaction transaction = newBuyTransaction();
        transaction.submit("EX_ORDER_1");
        transaction.fill(new BigDecimal("2.50000000"), new BigDecimal("10.20000000"));

        conciliation.processFill(
                transaction,
                orderData(
                        transaction.getClientOrderId(),
                        OrderDataDto.OrderStatus.FILLED,
                        new BigDecimal("2.50000000"),
                        new BigDecimal("10.30000000")
                ),
                true
        );

        assertEquals(TransactionStatus.FILLED, transaction.getStatus());
        assertAmount("2.50000000", transaction.getExecutedQuantity());
        assertAmount("10.20000000", transaction.getExecutedPrice());
        verifyNoInteractions(repository, publisher);
    }

    @Test
    void executeRejectsInvalidTransitionToCanceledFromFilled() {
        StrategyRunnerRepositoryPort repository = mock(StrategyRunnerRepositoryPort.class);
        EventPublisherPort publisher = mock(EventPublisherPort.class);
        ConciliationOrderUpdate conciliation = new ConciliationOrderUpdate(repository, publisher);

        Transaction transaction = newBuyTransaction();
        transaction.submit("EX_ORDER_1");
        transaction.fill(new BigDecimal("2.50000000"), new BigDecimal("10.20000000"));
        when(repository.findTransactionByClientOrderId(transaction.getClientOrderId()))
                .thenReturn(Optional.of(transaction));

        assertThrows(IllegalStateException.class, () ->
                conciliation.execute(
                        orderData(transaction.getClientOrderId(), OrderDataDto.OrderStatus.CANCELED,
                                transaction.getExecutedQuantity(), transaction.getExecutedPrice()),
                        tx -> {
                        },
                        (tx, od, finalFill) -> {
                        },
                        tx -> {
                        }
                )
        );
    }

    private static Transaction newBuyTransaction() {
        BigDecimal quantity = new BigDecimal("2.50000000");
        BigDecimal price = new BigDecimal("10.00000000");
        return new Transaction(
                UUID.randomUUID(),
                ClientOrderId.generate("x1", TransactionType.BUY),
                TransactionType.BUY,
                "BTCUSDT",
                quantity,
                price,
                quantity.multiply(price),
                new BigDecimal("0.8"),
                "test",
                null
        );
    }

    private static OrderDataDto orderData(String clientOrderId,
                                          OrderDataDto.OrderStatus status,
                                          BigDecimal executedQty,
                                          BigDecimal executedPrice) {
        return new OrderDataDto(
                "EX_ORDER_1",
                clientOrderId,
                Symbol.of("BTCUSDT"),
                OrderDataDto.OrderSide.BUY,
                OrderDataDto.OrderType.LIMIT,
                new BigDecimal("2.50000000"),
                executedQty,
                new BigDecimal("10.00000000"),
                executedPrice,
                BigDecimal.ZERO,
                status,
                null,
                Instant.now()
        );
    }

    private static void assertAmount(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }
}

