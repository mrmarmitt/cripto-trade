package com.marmitt.core.application.usecase.runner.processsignal;

import com.marmitt.core.domain.runner.Position;
import com.marmitt.core.domain.runner.StrategyRunner;
import com.marmitt.core.domain.runner.Transaction;
import com.marmitt.core.dto.runner.OrderCancelCommand;
import com.marmitt.core.dto.strategy.StrategyOutputDto;
import com.marmitt.core.enums.OrderType;
import com.marmitt.core.enums.TradingAction;
import com.marmitt.core.enums.TransactionStatus;
import com.marmitt.core.ports.outbound.exchange.OrderDispatchPort;
import com.marmitt.core.ports.outbound.repository.StrategyRunnerRepositoryPort;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T45: acoes do Safe Mode sobre um runner.
 */
class SafeModeHandlerTest {

    private final StrategyRunnerRepositoryPort repository = mock(StrategyRunnerRepositoryPort.class);
    private final OrderDispatchPort orderDispatch = mock(OrderDispatchPort.class);
    private final TradeIntentFactory intentFactory = new TradeIntentFactory();
    private final SafeModeHandler handler = new SafeModeHandler(repository, intentFactory,
            new CancelSignalHandler(repository, intentFactory, orderDispatch));
    private final StrategyRunner runner = runner();

    @Test
    void cancelsOnlyRestingLimitOrders() {
        List<Transaction> inFlight = List.of(tx("LIMIT_1", OrderType.LIMIT), tx("MARKET_1", OrderType.MARKET),
                tx("LIMIT_2", OrderType.LIMIT));
        when(repository.findByRunnerIdAndStatuses(runner.getId(), TransactionStatus.IN_FLIGHT)).thenReturn(inFlight);

        handler.cancelRestingOrders(runner);

        ArgumentCaptor<OrderCancelCommand> commands = ArgumentCaptor.forClass(OrderCancelCommand.class);
        verify(orderDispatch, times(2)).cancel(commands.capture());
        assertEquals(List.of("LIMIT_1", "LIMIT_2"),
                commands.getAllValues().stream().map(OrderCancelCommand::clientOrderId).toList());
    }

    @Test
    void cancelFailureDoesNotStopTheOthers() {
        List<Transaction> inFlight = List.of(tx("LIMIT_1", OrderType.LIMIT), tx("LIMIT_2", OrderType.LIMIT));
        when(repository.findByRunnerIdAndStatuses(runner.getId(), TransactionStatus.IN_FLIGHT)).thenReturn(inFlight);
        doThrow(new IllegalStateException("exchange down")).doNothing().when(orderDispatch).cancel(any());

        handler.cancelRestingOrders(runner);

        verify(orderDispatch, times(2)).cancel(any());
    }

    @Test
    void liquidatesWholeUnlockedLotsOnly() {
        Position free = position(false, "0.00099900");
        Position locked = position(true, "0.00100000");
        when(repository.findOpenPositionsByRunnerId(runner.getId())).thenReturn(List.of(free, locked));

        List<StrategyOutputDto> signals = handler.liquidationSignals(runner);

        assertEquals(1, signals.size());
        StrategyOutputDto sell = signals.getFirst();
        assertEquals(TradingAction.SHOULD_SELL, sell.decision());
        assertEquals(free.getId(), sell.targetLotId());
        assertEquals(0, new BigDecimal("0.00099900").compareTo(sell.quantity()));
        assertEquals(SafeModeHandler.STRATEGY_NAME, sell.strategyName());
        assertEquals(OrderType.MARKET, intentFactory.liquidationOrderType());
    }

    private static StrategyRunner runner() {
        StrategyRunner runner = mock(StrategyRunner.class);
        when(runner.getId()).thenReturn(UUID.randomUUID());
        when(runner.getSymbol()).thenReturn("BTCUSDT");
        when(runner.getExchangeId()).thenReturn("MOCK");
        return runner;
    }

    private static Transaction tx(String clientOrderId, OrderType orderType) {
        Transaction transaction = mock(Transaction.class);
        when(transaction.getId()).thenReturn(UUID.randomUUID());
        when(transaction.getClientOrderId()).thenReturn(clientOrderId);
        when(transaction.getOrderType()).thenReturn(orderType);
        when(transaction.getStatus()).thenReturn(TransactionStatus.SUBMITTED);
        return transaction;
    }

    private static Position position(boolean locked, String quantity) {
        Position position = mock(Position.class);
        when(position.getId()).thenReturn(UUID.randomUUID());
        when(position.isLocked()).thenReturn(locked);
        when(position.getQuantity()).thenReturn(new BigDecimal(quantity));
        return position;
    }
}
