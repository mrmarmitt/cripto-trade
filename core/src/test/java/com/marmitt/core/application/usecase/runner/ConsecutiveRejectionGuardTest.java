package com.marmitt.core.application.usecase.runner;

import com.marmitt.core.domain.Symbol;
import com.marmitt.core.domain.runner.ClientOrderId;
import com.marmitt.core.domain.runner.Transaction;
import com.marmitt.core.dto.notification.ErrorNotificationType;
import com.marmitt.core.dto.runner.response.RunnerTransitionResult;
import com.marmitt.core.dto.websocket.data.OrderDataDto;
import com.marmitt.core.enums.RunnerStatus;
import com.marmitt.core.enums.TransactionType;
import com.marmitt.core.ports.inbound.runner.HaltRunnerPort;
import com.marmitt.core.ports.outbound.notification.ErrorNotificationPort;
import com.marmitt.core.ports.outbound.repository.StrategyRunnerRepositoryPort;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T25: rejeicoes consecutivas param so o runner, com retomada manual.
 */
class ConsecutiveRejectionGuardTest {

    private static final String SHORT_CODE = "ab12";
    private static final UUID RUNNER = UUID.randomUUID();

    private final StrategyRunnerRepositoryPort repository = mock(StrategyRunnerRepositoryPort.class);
    private final HaltRunnerPort haltRunner = mock(HaltRunnerPort.class);
    private final ErrorNotificationPort notification = mock(ErrorNotificationPort.class);
    private final ConsecutiveRejectionGuard guard =
            new ConsecutiveRejectionGuard(repository, haltRunner, notification, Clock.systemUTC(), 5);

    ConsecutiveRejectionGuardTest() {
        Transaction transaction = mock(Transaction.class);
        when(transaction.getRunnerId()).thenReturn(RUNNER);
        when(repository.findTransactionByClientOrderId(anyString())).thenReturn(Optional.of(transaction));
        when(haltRunner.haltById(RUNNER))
                .thenReturn(new RunnerTransitionResult(RUNNER, "s", "BTCUSDT", RunnerStatus.ACTIVE, RunnerStatus.HALTED));
    }

    @Test
    void fiveDistinctRejectionsInARowHaltTheRunnerAndAlert() {
        for (int i = 0; i < 4; i++) {
            guard.onOrderUpdate(order(OrderDataDto.OrderStatus.REJECTED));
        }
        verify(haltRunner, never()).haltById(any());

        guard.onOrderUpdate(order(OrderDataDto.OrderStatus.REJECTED));

        verify(haltRunner).haltById(RUNNER);
        verify(notification).notifyError(argThat(event -> event.type() == ErrorNotificationType.RUNNER_AUTO_HALTED
                && RUNNER.equals(event.runnerId())));
    }

    @Test
    void acceptedOrderResetsTheStreak() {
        for (int i = 0; i < 4; i++) {
            guard.onOrderUpdate(order(OrderDataDto.OrderStatus.REJECTED));
        }
        guard.onOrderUpdate(order(OrderDataDto.OrderStatus.NEW));
        for (int i = 0; i < 4; i++) {
            guard.onOrderUpdate(order(OrderDataDto.OrderStatus.REJECTED));
        }

        verify(haltRunner, never()).haltById(any());
    }

    @Test
    void repeatedRejectedEventOfTheSameOrderCountsOnce() {
        OrderDataDto same = order(OrderDataDto.OrderStatus.REJECTED);
        for (int i = 0; i < 6; i++) {
            guard.onOrderUpdate(same);
        }

        verify(haltRunner, never()).haltById(any());
    }

    @Test
    void runnerAlreadyHaltedIsNotAlertedAgain() {
        when(haltRunner.haltById(RUNNER))
                .thenReturn(new RunnerTransitionResult(RUNNER, "s", "BTCUSDT", RunnerStatus.HALTED, RunnerStatus.HALTED));
        for (int i = 0; i < 5; i++) {
            guard.onOrderUpdate(order(OrderDataDto.OrderStatus.REJECTED));
        }

        verify(haltRunner, times(1)).haltById(RUNNER);
        verify(notification, never()).notifyError(any());
    }

    private static OrderDataDto order(OrderDataDto.OrderStatus status) {
        return new OrderDataDto(null, ClientOrderId.generate(SHORT_CODE, TransactionType.BUY), Symbol.of("BTCUSDT"),
                OrderDataDto.OrderSide.BUY, OrderDataDto.OrderType.LIMIT, new BigDecimal("0.001"), BigDecimal.ZERO,
                new BigDecimal("65000"), BigDecimal.ZERO, BigDecimal.ZERO, status,
                status == OrderDataDto.OrderStatus.REJECTED ? "insufficient balance" : null, Instant.now(), null);
    }
}
