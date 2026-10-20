package com.marmitt.core.application.usecase.runner.processsignal;

import com.marmitt.core.domain.runner.LiquidationPolicy;
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
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T45: acoes do Safe Mode sobre um runner; T48: politica de liquidacao LIMIT com escalada.
 */
class SafeModeHandlerTest {

    private final StrategyRunnerRepositoryPort repository = mock(StrategyRunnerRepositoryPort.class);
    private final OrderDispatchPort orderDispatch = mock(OrderDispatchPort.class);
    private final TradeIntentFactory intentFactory = new TradeIntentFactory();
    private final SafeModeHandler handler = new SafeModeHandler(repository, intentFactory,
            new CancelSignalHandler(repository, intentFactory, orderDispatch));
    private final StrategyRunner runner = runner();

    private static final BigDecimal PRICE = new BigDecimal("65000");
    private static final UUID PORTFOLIO_ID = UUID.randomUUID();

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
    void marketPolicyLiquidatesWholeUnlockedLotsOnly() {
        Position free = position(false, "0.00099900");
        Position locked = position(true, "0.00100000");
        when(repository.findActivePositionsByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of(free, locked));

        List<SafeModeHandler.Liquidation> liquidations = handler.liquidations(runner, PRICE);

        assertEquals(1, liquidations.size());
        SafeModeHandler.Liquidation liquidation = liquidations.getFirst();
        StrategyOutputDto sell = liquidation.signal();
        assertEquals(OrderType.MARKET, liquidation.orderType());
        assertEquals(TradingAction.SHOULD_SELL, sell.decision());
        assertEquals(free.getId(), sell.targetLotId());
        assertEquals(0, new BigDecimal("0.00099900").compareTo(sell.quantity()));
        assertNull(sell.limitPrice());
        assertEquals(SafeModeHandler.STRATEGY_NAME, sell.strategyName());
    }

    @Test
    void lotsOfOtherRunnersInThePortfolioAreIgnored() {
        Position mine = position(false, "0.001");
        Position other = position(UUID.randomUUID(), false, "0.002");
        when(repository.findActivePositionsByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of(mine, other));

        List<SafeModeHandler.Liquidation> liquidations = handler.liquidations(runner, PRICE);

        assertEquals(List.of(mine.getId()), liquidations.stream().map(l -> l.signal().targetLotId()).toList());
    }

    @Test
    void limitPolicySendsOneLimitBelowTheTickPrice() {
        useLimitPolicy();
        Position lot = position(false, "0.001");
        when(repository.findActivePositionsByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of(lot));

        SafeModeHandler.Liquidation liquidation = handler.liquidations(runner, PRICE).getFirst();

        assertEquals(OrderType.LIMIT, liquidation.orderType());
        assertEquals(lot.getId(), liquidation.signal().targetLotId());
        assertEquals(0, new BigDecimal("64350").compareTo(liquidation.signal().limitPrice()));
    }

    @Test
    void limitInFlightIsKeptUntilTheDeadlineThenCanceled() {
        useLimitPolicy();
        Position lot = position(false, "0.001");
        when(repository.findActivePositionsByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of(lot));
        handler.liquidations(runner, PRICE);

        Transaction liquidationSell = tx("SELL_LIMIT", OrderType.LIMIT);
        when(liquidationSell.isSell()).thenReturn(true);
        UUID lotId = lot.getId();
        when(liquidationSell.getTargetLotId()).thenReturn(lotId);
        lockBy(lot, liquidationSell);
        when(repository.findByRunnerIdAndStatuses(runner.getId(), TransactionStatus.IN_FLIGHT))
                .thenReturn(List.of(liquidationSell));

        for (int tick = 1; tick <= 2; tick++) {
            handler.cancelRestingOrders(runner);
            assertTrue(handler.liquidations(runner, PRICE).isEmpty(), "lote travado nao e vendido de novo");
        }
        verify(orderDispatch, never()).cancel(any());

        handler.cancelRestingOrders(runner);
        assertTrue(handler.liquidations(runner, PRICE).isEmpty());
        verify(orderDispatch, times(1)).cancel(any());
    }

    @Test
    void unlockedLotAfterTheLimitAttemptEscalatesToMarket() {
        useLimitPolicy();
        Position lot = position(false, "0.001");
        when(repository.findActivePositionsByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of(lot));
        handler.liquidations(runner, PRICE);

        when(lot.getQuantity()).thenReturn(new BigDecimal("0.0004"));
        SafeModeHandler.Liquidation escalation = handler.liquidations(runner, PRICE).getFirst();

        assertEquals(OrderType.MARKET, escalation.orderType());
        assertNull(escalation.signal().limitPrice());
        assertEquals(0, new BigDecimal("0.0004").compareTo(escalation.signal().quantity()),
                "vende so o restante apos fill parcial");
    }

    @Test
    void forgetStartsANewLimitAttempt() {
        useLimitPolicy();
        Position lot = position(false, "0.001");
        when(repository.findActivePositionsByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of(lot));
        handler.liquidations(runner, PRICE);

        handler.forget(runner.getId());

        assertEquals(OrderType.LIMIT, handler.liquidations(runner, PRICE).getFirst().orderType());
    }

    @Test
    void strategyFifoLimitWithoutTargetLotIsCanceled() {
        useLimitPolicy();
        Position lot = position(false, "0.001");
        when(repository.findActivePositionsByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of(lot));
        handler.liquidations(runner, PRICE);
        Transaction strategySell = tx("STRATEGY_SELL", OrderType.LIMIT);
        when(strategySell.isSell()).thenReturn(true);
        when(strategySell.getTargetLotId()).thenReturn(null);
        when(repository.findByRunnerIdAndStatuses(runner.getId(), TransactionStatus.IN_FLIGHT))
                .thenReturn(List.of(strategySell));

        handler.cancelRestingOrders(runner);

        verify(orderDispatch).cancel(any());
    }

    private void useLimitPolicy() {
        when(runner.getLiquidationPolicy())
                .thenReturn(new LiquidationPolicy(OrderType.LIMIT, new BigDecimal("0.01"), 3));
    }

    private void lockBy(Position lot, Transaction transaction) {
        UUID transactionId = transaction.getId();
        when(lot.isLocked()).thenReturn(true);
        when(lot.getLockedByTransactionId()).thenReturn(transactionId);
        when(repository.findTransactionById(transactionId)).thenReturn(Optional.of(transaction));
    }

    private static StrategyRunner runner() {
        StrategyRunner runner = mock(StrategyRunner.class);
        when(runner.getId()).thenReturn(UUID.randomUUID());
        when(runner.getSymbol()).thenReturn("BTCUSDT");
        when(runner.getExchangeId()).thenReturn("MOCK");
        when(runner.getPortfolioId()).thenReturn(PORTFOLIO_ID);
        when(runner.getLiquidationPolicy()).thenReturn(LiquidationPolicy.defaults());
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

    private Position position(boolean locked, String quantity) {
        return position(runner.getId(), locked, quantity);
    }

    private static Position position(UUID runnerId, boolean locked, String quantity) {
        Position position = mock(Position.class);
        when(position.getId()).thenReturn(UUID.randomUUID());
        when(position.getRunnerId()).thenReturn(runnerId);
        when(position.isLocked()).thenReturn(locked);
        when(position.getQuantity()).thenReturn(new BigDecimal(quantity));
        return position;
    }
}
