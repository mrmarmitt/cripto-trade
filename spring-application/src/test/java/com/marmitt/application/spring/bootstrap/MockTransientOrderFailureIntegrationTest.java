package com.marmitt.application.spring.bootstrap;

import com.marmitt.application.spring.config.exchange.MockExchangeAdapter;
import com.marmitt.core.application.usecase.runner.OrderConciliationUseCase;
import com.marmitt.core.application.usecase.runner.orderconciliation.ConciliationOrderUpdateExecutor;
import com.marmitt.core.domain.runner.StrategyRunner;
import com.marmitt.core.domain.runner.Transaction;
import com.marmitt.core.dto.runner.OrderDispatchCommand;
import com.marmitt.core.dto.websocket.data.OrderDataDto;
import com.marmitt.core.enums.TransactionStatus;
import com.marmitt.core.enums.TransactionType;
import com.marmitt.mock.config.MockOrderScenarioOverride;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ContextConfiguration;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ContextConfiguration(classes = MockTransientOrderFailureIntegrationTest.TransientFailureTestConfig.class)
class MockTransientOrderFailureIntegrationTest extends MockOrderOverrideIntegrationTestSupport {

    private static final String SCENARIO_TRANSIENT_SELL_FILLED_REDELIVERY =
            "phase2 transient sell filled redelivery";

    @Autowired
    private PlannedOrderConciliationFailurePlan failurePlan;

    @Test
    void exchangeRedeliveredSellFilledAfterTransientFailureShouldApplyEconomicEffectsOnce() {
        UUID portfolioId = createPortfolio();
        StrategyRunner runner = createAndActivateRunner(portfolioId);

        BigDecimal quantity = new BigDecimal("0.00200000");
        BigDecimal buyPrice = new BigDecimal("65000.00000000");
        BigDecimal sellPrice = new BigDecimal("66200.00000000");
        BigDecimal expectedPnl = quantity.multiply(sellPrice.subtract(buyPrice));

        SellSetup sellSetup = createFilledBuyAndLockedSell(
                portfolioId,
                runner,
                quantity,
                buyPrice,
                sellPrice,
                SCENARIO_TRANSIENT_SELL_FILLED_REDELIVERY
        );

        String sellClientOrderId = sellSetup.sellTransaction().getClientOrderId();
        failurePlan.failOnce(sellClientOrderId, OrderDataDto.OrderStatus.FILLED);

        MockExchangeAdapter mockExchangeAdapter = getMockExchangeAdapter();
        mockExchangeAdapter.registerOrderScenarioOverride(sellClientOrderId, new MockOrderScenarioOverride(
                List.of(
                        new MockOrderScenarioOverride.PlannedEvent(
                                OrderDataDto.OrderStatus.FILLED,
                                quantity,
                                sellPrice,
                                BigDecimal.ZERO,
                                null,
                                20L,
                                // The order callback path has no internal retry.
                                // This duplicate intentionally simulates exchange/mock redelivery
                                // after the first local processing failure.
                                1
                        )
                ),
                MockOrderScenarioOverride.EventOrdering.AS_IS
        ));

        orderDispatchPort.dispatch(new OrderDispatchCommand(
                sellClientOrderId,
                runner.getId(),
                SYMBOL,
                MOCK_EXCHANGE,
                TransactionType.SELL,
                quantity,
                sellPrice
        ));

        SellStateSnapshot stable = awaitStableSellState(
                portfolioId,
                sellSetup.sellTransaction().getId(),
                sellSetup.position().id(),
                WAIT_TIMEOUT,
                quantity,
                expectedPnl
        );

        assertEquals(1, failurePlan.failureCount(sellClientOrderId, OrderDataDto.OrderStatus.FILLED),
                "The test must prove the first SELL FILLED delivery failed before redelivery");
        assertEquals(TransactionStatus.FILLED, stable.status());
        assertEquals(0, stable.executedQuantity().compareTo(quantity));
        assertEquals(1, stable.matchCount(),
                "Redelivered SELL FILLED must persist exactly one transaction_match");
        assertEquals(0, stable.matchedQuantity().compareTo(quantity));
        assertEquals(0, stable.pnlRealized().compareTo(expectedPnl));
        assertEquals("CLOSED", stable.positionStatus());
        assertEquals(0, stable.positionQuantity().compareTo(BigDecimal.ZERO));
        assertEquals(0, stable.realizedBalance().compareTo(expectedPnl));
        assertEquals(0, stable.availableBalance().compareTo(INITIAL_CAPITAL.add(expectedPnl)));
        assertEquals(0, stable.reservedBalance().compareTo(BigDecimal.ZERO));
        assertEquals(0, countDeadLetterEntries(),
                "A successful redelivery must not create dead-letter entries");
    }

    private int countDeadLetterEntries() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM dead_letter_entries", Integer.class);
        return count == null ? 0 : count;
    }

    static class TransientFailureTestConfig {

        @Bean
        PlannedOrderConciliationFailurePlan plannedOrderConciliationFailurePlan() {
            return new PlannedOrderConciliationFailurePlan();
        }

        @Bean
        @Primary
        OrderConciliationUseCase failingOrderConciliationUseCase(
                ConciliationOrderUpdateExecutor delegate,
                PlannedOrderConciliationFailurePlan failurePlan
        ) {
            return new OrderConciliationUseCase(
                    new FailingOnceConciliationOrderUpdateExecutor(delegate, failurePlan));
        }
    }

    static class FailingOnceConciliationOrderUpdateExecutor implements ConciliationOrderUpdateExecutor {

        private final ConciliationOrderUpdateExecutor delegate;
        private final PlannedOrderConciliationFailurePlan failurePlan;

        FailingOnceConciliationOrderUpdateExecutor(ConciliationOrderUpdateExecutor delegate,
                                                  PlannedOrderConciliationFailurePlan failurePlan) {
            this.delegate = delegate;
            this.failurePlan = failurePlan;
        }

        @Override
        public void execute(OrderDataDto orderData) {
            if (failurePlan.shouldFail(orderData)) {
                throw new PlannedTransientOrderConciliationException(
                        "planned transient order conciliation failure for clientOrderId="
                                + orderData.clientOrderId() + " status=" + orderData.status());
            }
            delegate.execute(orderData);
        }

        @Override
        public void submitTransaction(Transaction transaction) {
            delegate.submitTransaction(transaction);
        }

        @Override
        public void processFill(Transaction transaction, OrderDataDto orderData, boolean isFinal) {
            delegate.processFill(transaction, orderData, isFinal);
        }

        @Override
        public void releaseMargin(Transaction transaction) {
            delegate.releaseMargin(transaction);
        }
    }

    static class PlannedOrderConciliationFailurePlan {

        private final Set<FailureKey> remainingFailures = ConcurrentHashMap.newKeySet();
        private final ConcurrentHashMap<FailureKey, AtomicInteger> failures = new ConcurrentHashMap<>();

        void failOnce(String clientOrderId, OrderDataDto.OrderStatus status) {
            remainingFailures.add(new FailureKey(clientOrderId, status));
        }

        boolean shouldFail(OrderDataDto orderData) {
            FailureKey key = new FailureKey(orderData.clientOrderId(), orderData.status());
            if (!remainingFailures.remove(key)) {
                return false;
            }
            failures.computeIfAbsent(key, ignored -> new AtomicInteger()).incrementAndGet();
            return true;
        }

        int failureCount(String clientOrderId, OrderDataDto.OrderStatus status) {
            AtomicInteger count = failures.get(new FailureKey(clientOrderId, status));
            return count == null ? 0 : count.get();
        }
    }

    static class PlannedTransientOrderConciliationException extends RuntimeException {
        PlannedTransientOrderConciliationException(String message) {
            super(message);
        }
    }

    private record FailureKey(String clientOrderId, OrderDataDto.OrderStatus status) {
    }
}
