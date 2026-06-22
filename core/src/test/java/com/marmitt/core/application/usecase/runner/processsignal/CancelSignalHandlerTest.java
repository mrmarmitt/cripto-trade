package com.marmitt.core.application.usecase.runner.processsignal;

import com.marmitt.core.domain.runner.StrategyRunner;
import com.marmitt.core.domain.runner.Transaction;
import com.marmitt.core.dto.runner.OrderCancelCommand;
import com.marmitt.core.dto.strategy.StrategyOutputDto;
import com.marmitt.core.enums.TransactionStatus;
import com.marmitt.core.enums.TransactionType;
import com.marmitt.core.ports.outbound.exchange.OrderDispatchPort;
import com.marmitt.core.ports.outbound.repository.StrategyRunnerRepositoryPort;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CancelSignalHandlerTest {

    private final StrategyRunnerRepositoryPort repository = mock(StrategyRunnerRepositoryPort.class);
    private final OrderDispatchPort orderDispatch = mock(OrderDispatchPort.class);
    private final CancelSignalHandler handler = new CancelSignalHandler(repository, orderDispatch);

    private final UUID runnerId = UUID.randomUUID();

    @Test
    void dispatchesCancelForOwnedCancelableTransaction() {
        Transaction tx = tx(runnerId, TransactionStatus.SUBMITTED);
        when(repository.findTransactionById(tx.getId())).thenReturn(Optional.of(tx));

        handler.handle(runner(runnerId), StrategyOutputDto.cancel("s", tx.getId(), "thesis changed"));

        ArgumentCaptor<OrderCancelCommand> captor = ArgumentCaptor.forClass(OrderCancelCommand.class);
        verify(orderDispatch).cancel(captor.capture());
        assertEquals(tx.getClientOrderId(), captor.getValue().clientOrderId());
        assertEquals(runnerId, captor.getValue().runnerId());
        assertEquals("BTCUSDT", captor.getValue().symbol());
        assertEquals("BINANCE", captor.getValue().exchangeId());
    }

    @Test
    void dispatchesCancelForPendingAndPartial() {
        Transaction pending = tx(runnerId, TransactionStatus.PENDING);
        Transaction partial = tx(runnerId, TransactionStatus.PARTIAL);
        when(repository.findTransactionById(pending.getId())).thenReturn(Optional.of(pending));
        when(repository.findTransactionById(partial.getId())).thenReturn(Optional.of(partial));

        handler.handle(runner(runnerId), StrategyOutputDto.cancel("s", pending.getId(), "x"));
        handler.handle(runner(runnerId), StrategyOutputDto.cancel("s", partial.getId(), "x"));

        verify(orderDispatch, org.mockito.Mockito.times(2)).cancel(any());
    }

    @Test
    void ignoresWhenTargetNotFound() {
        UUID targetId = UUID.randomUUID();
        when(repository.findTransactionById(targetId)).thenReturn(Optional.empty());

        handler.handle(runner(runnerId), StrategyOutputDto.cancel("s", targetId, "x"));

        verify(orderDispatch, never()).cancel(any());
    }

    @Test
    void ignoresWhenTargetBelongsToAnotherRunner() {
        Transaction tx = tx(UUID.randomUUID(), TransactionStatus.SUBMITTED); // dono diferente
        when(repository.findTransactionById(tx.getId())).thenReturn(Optional.of(tx));

        handler.handle(runner(runnerId), StrategyOutputDto.cancel("s", tx.getId(), "x"));

        verify(orderDispatch, never()).cancel(any());
    }

    @Test
    void ignoresWhenTargetIsTerminal() {
        Transaction tx = tx(runnerId, TransactionStatus.FILLED);
        when(repository.findTransactionById(tx.getId())).thenReturn(Optional.of(tx));

        handler.handle(runner(runnerId), StrategyOutputDto.cancel("s", tx.getId(), "x"));

        verify(orderDispatch, never()).cancel(any());
    }

    private StrategyRunner runner(UUID id) {
        StrategyRunner runner = mock(StrategyRunner.class);
        when(runner.getId()).thenReturn(id);
        when(runner.getSymbol()).thenReturn("BTCUSDT");
        when(runner.getExchangeId()).thenReturn("BINANCE");
        return runner;
    }

    private Transaction tx(UUID ownerRunnerId, TransactionStatus status) {
        return Transaction.reconstitute()
                .id(UUID.randomUUID())
                .runnerId(ownerRunnerId)
                .clientOrderId("client-order-id")
                .exchangeOrderId(status == TransactionStatus.PENDING ? null : "EX_ORDER")
                .status(status)
                .type(TransactionType.BUY)
                .symbol("BTCUSDT")
                .quantity(new BigDecimal("0.0015"))
                .price(new BigDecimal("60000"))
                .total(new BigDecimal("90"))
                .requestedAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }
}
