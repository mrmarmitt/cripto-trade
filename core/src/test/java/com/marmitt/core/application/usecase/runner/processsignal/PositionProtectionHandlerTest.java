package com.marmitt.core.application.usecase.runner.processsignal;

import com.marmitt.core.domain.runner.Position;
import com.marmitt.core.domain.runner.PositionProtection;
import com.marmitt.core.domain.runner.StrategyRunner;
import com.marmitt.core.domain.runner.Transaction;
import com.marmitt.core.dto.runner.OrderCancelCommand;
import com.marmitt.core.dto.strategy.StrategyOutputDto;
import com.marmitt.core.enums.OrderType;
import com.marmitt.core.enums.PositionStatus;
import com.marmitt.core.enums.TradingAction;
import com.marmitt.core.enums.TransactionStatus;
import com.marmitt.core.ports.outbound.exchange.OrderDispatchPort;
import com.marmitt.core.ports.outbound.repository.StrategyRunnerRepositoryPort;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T44: protecao do lote — gatilho, saida MARKET do lote inteiro e liberacao do lote travado por LIMIT.
 */
class PositionProtectionHandlerTest {

    private static final BigDecimal CRASH = new BigDecimal("60000");

    private final StrategyRunnerRepositoryPort repository = mock(StrategyRunnerRepositoryPort.class);
    private final OrderDispatchPort orderDispatch = mock(OrderDispatchPort.class);
    private final TradeIntentFactory intentFactory = new TradeIntentFactory();
    private final PositionProtectionHandler handler = new PositionProtectionHandler(repository, intentFactory,
            new CancelSignalHandler(repository, intentFactory, orderDispatch));
    private final StrategyRunner runner = runner();

    @Test
    void noActiveLotOrLotWithinLimitsHasNoTrigger() {
        when(repository.findActivePositionByRunnerIdAndSymbol(runner.getId(), "BTCUSDT")).thenReturn(Optional.empty());
        assertTrue(handler.findTrigger(runner, CRASH, Instant.now()).isEmpty());

        when(repository.findActivePositionByRunnerIdAndSymbol(runner.getId(), "BTCUSDT"))
                .thenReturn(Optional.of(lot(null)));
        assertTrue(handler.findTrigger(runner, new BigDecimal("64000"), Instant.now()).isEmpty());
    }

    @Test
    void violatedLotBecomesAWholeLotSellAtMarket() {
        Position lot = lot(null);
        when(repository.findActivePositionByRunnerIdAndSymbol(runner.getId(), "BTCUSDT")).thenReturn(Optional.of(lot));

        PositionProtectionHandler.Trigger trigger = handler.findTrigger(runner, CRASH, Instant.now()).orElseThrow();
        StrategyOutputDto exit = handler.exitSignal(trigger);

        assertEquals(PositionProtection.Reason.MAX_LOSS, trigger.violation().reason());
        assertEquals(TradingAction.SHOULD_SELL, exit.decision());
        assertEquals(lot.getId(), exit.targetLotId());
        assertEquals(0, lot.getQuantity().compareTo(exit.quantity()));
        assertEquals(PositionProtectionHandler.STRATEGY_NAME, exit.strategyName());
        assertTrue(exit.reasoning().startsWith("protective exit MAX_LOSS"));
        assertEquals(null, exit.limitPrice(), "Saida a mercado, sem preco limite");
        assertEquals(OrderType.MARKET, intentFactory.liquidationOrderType());
    }

    @Test
    void lotLockedByARestingLimitSellIsReleasedByCancelingIt() {
        Transaction restingSell = sell(OrderType.LIMIT, TransactionStatus.SUBMITTED);
        PositionProtectionHandler.Trigger trigger = lockedTrigger(restingSell);

        assertTrue(handler.releaseLot(runner, trigger));

        ArgumentCaptor<OrderCancelCommand> command = ArgumentCaptor.forClass(OrderCancelCommand.class);
        verify(orderDispatch).cancel(command.capture());
        assertEquals(restingSell.getClientOrderId(), command.getValue().clientOrderId());
    }

    @Test
    void lotLockedByAMarketSellIsLeftAlone() {
        assertFalse(handler.releaseLot(runner, lockedTrigger(sell(OrderType.MARKET, TransactionStatus.SUBMITTED))));
        assertFalse(handler.releaseLot(runner, lockedTrigger(sell(OrderType.LIMIT, TransactionStatus.FILLED))),
                "SELL que ja saiu de voo nao se cancela");
        verify(orderDispatch, never()).cancel(any());
    }

    private PositionProtectionHandler.Trigger lockedTrigger(Transaction lockingSell) {
        Position lot = lot(lockingSell.getId());
        when(repository.findTransactionById(lockingSell.getId())).thenReturn(Optional.of(lockingSell));
        return new PositionProtectionHandler.Trigger(lot,
                new PositionProtection.Violation(PositionProtection.Reason.MAX_LOSS, "test"));
    }

    private static StrategyRunner runner() {
        StrategyRunner runner = mock(StrategyRunner.class);
        when(runner.getId()).thenReturn(UUID.randomUUID());
        when(runner.getSymbol()).thenReturn("BTCUSDT");
        when(runner.getExchangeId()).thenReturn("MOCK");
        when(runner.getPositionProtection()).thenReturn(PositionProtection.defaults());
        return runner;
    }

    /** 0.001 BTC a 65000: 5% de perda abaixo de 61750. */
    private static Position lot(UUID lockedBy) {
        Instant now = Instant.now();
        return Position.reconstitute()
                .id(UUID.randomUUID()).runnerId(UUID.randomUUID()).symbol("BTCUSDT")
                .status(lockedBy == null ? PositionStatus.OPEN : PositionStatus.CLOSING)
                .quantity(new BigDecimal("0.001")).averagePrice(new BigDecimal("65000")).costBasis(new BigDecimal("65"))
                .realizedPnl(BigDecimal.ZERO).openedAt(now).updatedAt(now)
                .lockedByTransactionId(lockedBy).lockedQuantity(lockedBy == null ? null : new BigDecimal("0.001"))
                .build();
    }

    private static Transaction sell(OrderType orderType, TransactionStatus status) {
        Transaction transaction = mock(Transaction.class);
        when(transaction.getId()).thenReturn(UUID.randomUUID());
        when(transaction.getClientOrderId()).thenReturn("x1-S-" + orderType);
        when(transaction.getOrderType()).thenReturn(orderType);
        when(transaction.getStatus()).thenReturn(status);
        return transaction;
    }
}
