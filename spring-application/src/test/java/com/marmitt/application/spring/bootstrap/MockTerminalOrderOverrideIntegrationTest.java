package com.marmitt.application.spring.bootstrap;

import com.marmitt.application.spring.config.exchange.MockExchangeAdapter;
import com.marmitt.core.domain.runner.ClientOrderId;
import com.marmitt.core.domain.runner.StrategyRunner;
import com.marmitt.core.domain.runner.Transaction;
import com.marmitt.core.dto.runner.OrderDispatchCommand;
import com.marmitt.core.dto.websocket.data.OrderDataDto;
import com.marmitt.core.enums.TransactionStatus;
import com.marmitt.core.enums.TransactionType;
import com.marmitt.mock.config.MockOrderScenarioOverride;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
class MockTerminalOrderOverrideIntegrationTest extends MockOrderOverrideIntegrationTestSupport {

    @Test
    void rejectedBuyShouldReleaseReservedBalanceWithoutCreatingPosition() {
        UUID portfolioId = createPortfolio();
        StrategyRunner runner = createAndActivateRunner(portfolioId);
        UUID runnerId = runner.getId();

        BigDecimal quantity = new BigDecimal("0.00200000");
        BigDecimal price = new BigDecimal("65300.00000000");
        String clientOrderId = ClientOrderId.generate(runner.getShortCode(), TransactionType.BUY);

        Transaction pendingBuy = new Transaction(
                runnerId,
                clientOrderId,
                TransactionType.BUY,
                SYMBOL,
                quantity,
                price,
                quantity.multiply(price),
                new BigDecimal("0.90"),
                SCENARIO_REJECTED_FINANCIAL,
                null
        );
        strategyRunnerRepository.saveTransaction(pendingBuy);
        assertTrue(globalBalanceRepository.reserveAtomic(portfolioId, pendingBuy.getTotal()));
        assertNoInitialPositionOrMatch(pendingBuy.getId());

        MockExchangeAdapter mockExchangeAdapter = getMockExchangeAdapter();
        mockExchangeAdapter.registerOrderScenarioOverride(clientOrderId, new MockOrderScenarioOverride(
                List.of(
                        new MockOrderScenarioOverride.PlannedEvent(
                                com.marmitt.core.dto.websocket.data.OrderDataDto.OrderStatus.REJECTED,
                                BigDecimal.ZERO,
                                price,
                                BigDecimal.ZERO,
                                "MOCK_REJECT_TEST",
                                20L,
                                0
                        )
                ),
                MockOrderScenarioOverride.EventOrdering.AS_IS
        ));

        orderDispatchPort.dispatch(new OrderDispatchCommand(
                clientOrderId,
                runnerId,
                SYMBOL,
                MOCK_EXCHANGE,
                TransactionType.BUY,
                quantity,
                price
        ));

        Transaction rejected = awaitTransactionStatus(pendingBuy.getId(), TransactionStatus.REJECTED, WAIT_TIMEOUT);
        assertEquals("MOCK_REJECT_TEST", rejected.getRejectReason());

        awaitBalanceState(portfolioId, INITIAL_CAPITAL, BigDecimal.ZERO, WAIT_TIMEOUT);
        assertEquals(0, countOpenPositionsByOpenedByTransactionId(pendingBuy.getId()));
        assertEquals(0, countMatchesByTransactionId(pendingBuy.getId()));
    }

    @Test
    void expiredBuyShouldReleaseReservedBalanceWithoutCreatingPosition() {
        UUID portfolioId = createPortfolio();
        StrategyRunner runner = createAndActivateRunner(portfolioId);
        UUID runnerId = runner.getId();

        BigDecimal quantity = new BigDecimal("0.00200000");
        BigDecimal price = new BigDecimal("65400.00000000");
        String clientOrderId = ClientOrderId.generate(runner.getShortCode(), TransactionType.BUY);

        Transaction pendingBuy = new Transaction(
                runnerId,
                clientOrderId,
                TransactionType.BUY,
                SYMBOL,
                quantity,
                price,
                quantity.multiply(price),
                new BigDecimal("0.90"),
                SCENARIO_EXPIRED_FINANCIAL,
                null
        );
        strategyRunnerRepository.saveTransaction(pendingBuy);
        assertTrue(globalBalanceRepository.reserveAtomic(portfolioId, pendingBuy.getTotal()));
        assertNoInitialPositionOrMatch(pendingBuy.getId());

        MockExchangeAdapter mockExchangeAdapter = getMockExchangeAdapter();
        mockExchangeAdapter.registerOrderScenarioOverride(clientOrderId, new MockOrderScenarioOverride(
                List.of(
                        new MockOrderScenarioOverride.PlannedEvent(
                                com.marmitt.core.dto.websocket.data.OrderDataDto.OrderStatus.EXPIRED,
                                BigDecimal.ZERO,
                                price,
                                BigDecimal.ZERO,
                                null,
                                20L,
                                0
                        )
                ),
                MockOrderScenarioOverride.EventOrdering.AS_IS
        ));

        orderDispatchPort.dispatch(new OrderDispatchCommand(
                clientOrderId,
                runnerId,
                SYMBOL,
                MOCK_EXCHANGE,
                TransactionType.BUY,
                quantity,
                price
        ));

        Transaction expired = awaitTransactionStatus(pendingBuy.getId(), TransactionStatus.EXPIRED, WAIT_TIMEOUT);
        assertEquals(0, expired.getEffectiveExecutedQuantity().compareTo(BigDecimal.ZERO));

        awaitBalanceState(portfolioId, INITIAL_CAPITAL, BigDecimal.ZERO, WAIT_TIMEOUT);
        assertEquals(0, countOpenPositionsByOpenedByTransactionId(pendingBuy.getId()));
        assertEquals(0, countMatchesByTransactionId(pendingBuy.getId()));
    }
}
