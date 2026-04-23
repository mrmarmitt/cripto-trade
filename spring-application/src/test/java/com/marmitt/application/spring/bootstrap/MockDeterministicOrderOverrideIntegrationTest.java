package com.marmitt.application.spring.bootstrap;

import com.marmitt.application.spring.CTradeApplication;
import com.marmitt.application.spring.config.exchange.MockExchangeAdapter;
import com.marmitt.core.domain.runner.ClientOrderId;
import com.marmitt.core.domain.runner.StrategyRunner;
import com.marmitt.core.domain.runner.Transaction;
import com.marmitt.core.dto.portfolio.CreatePortfolioRequest;
import com.marmitt.core.dto.portfolio.CreatePortfolioResponse;
import com.marmitt.core.dto.runner.CreateRunnerRequest;
import com.marmitt.core.dto.runner.CreateRunnerResponse;
import com.marmitt.core.dto.runner.OrderDispatchCommand;
import com.marmitt.core.enums.TransactionStatus;
import com.marmitt.core.enums.TransactionType;
import com.marmitt.core.ports.inbound.portfolio.CreatePortfolioPort;
import com.marmitt.core.ports.inbound.runner.CreateRunnerPort;
import com.marmitt.core.ports.outbound.exchange.OrderDispatchPort;
import com.marmitt.core.ports.outbound.repository.ExchangeAdapterRepositoryPort;
import com.marmitt.core.ports.outbound.repository.GlobalBalanceRepositoryPort;
import com.marmitt.core.ports.outbound.repository.StrategyRunnerRepositoryPort;
import com.marmitt.mock.config.MockOrderScenarioOverride;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
@SpringBootTest(
        classes = CTradeApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class MockDeterministicOrderOverrideIntegrationTest {

    private static final UUID SMA_STRATEGY_ID =
            UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
    private static final String SYMBOL = "BTCUSDT";
    private static final String MOCK_EXCHANGE = "MOCK";
    private static final BigDecimal INITIAL_CAPITAL = new BigDecimal("10000.00");
    private static final Duration WAIT_TIMEOUT = Duration.ofSeconds(10);
    private static final long DEFAULT_POLL_INTERVAL_MS = 80L;
    private static final long FILLED_STABILITY_POLL_INTERVAL_MS = 50L;
    private static final String SCENARIO_DUPLICATE_PARTIAL = "phase1b deterministic override";
    private static final String SCENARIO_PARTIAL_FILLED_CONVERGENCE = "phase1b partial+filled convergence";
    private static final String SCENARIO_DUPLICATE_FILLED = "phase1b duplicate filled idempotency";
    private static final String SCENARIO_DUPLICATE_SELL_FILLED = "phase2 duplicate sell filled idempotency";
    private static final String SCENARIO_REORDERED_BUY_FILLED_BEFORE_PARTIAL =
            "phase2 reordered buy filled before partial";
    private static final String SCENARIO_REORDERED_SELL_FILLED_BEFORE_PARTIAL =
            "phase2 reordered sell filled before partial";
    private static final String SCENARIO_REJECTED_FINANCIAL = "phase1b rejected financial";
    private static final String SCENARIO_EXPIRED_FINANCIAL = "phase1b expired financial";

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("ctrade")
            .withUsername("ctrade")
            .withPassword("ctrade123");

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("runner.boot.orchestrator-enabled", () -> "false");
    }

    @Autowired
    private CreatePortfolioPort createPortfolioPort;

    @Autowired
    private CreateRunnerPort createRunnerPort;

    @Autowired
    private StrategyRunnerRepositoryPort strategyRunnerRepository;

    @Autowired
    private GlobalBalanceRepositoryPort globalBalanceRepository;

    @Autowired
    private ExchangeAdapterRepositoryPort exchangeAdapterRepository;

    @Autowired
    private OrderDispatchPort orderDispatchPort;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("TRUNCATE TABLE portfolios CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE capital_event_ledger");
    }

    @Test
    void shouldNotDoubleApplyQuantityWhenDuplicatePartialEventsOccur() {
        UUID portfolioId = createPortfolio();
        StrategyRunner runner = createAndActivateRunner(portfolioId);
        UUID runnerId = runner.getId();

        BigDecimal quantity = new BigDecimal("0.00200000");
        BigDecimal price = new BigDecimal("65000.00000000");
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
                SCENARIO_DUPLICATE_PARTIAL,
                null
        );
        strategyRunnerRepository.saveTransaction(pendingBuy);
        assertNoInitialPositionOrMatch(pendingBuy.getId());

        MockExchangeAdapter mockExchangeAdapter = getMockExchangeAdapter();
        mockExchangeAdapter.registerOrderScenarioOverride(clientOrderId, new MockOrderScenarioOverride(
                List.of(
                        new MockOrderScenarioOverride.PlannedEvent(
                                com.marmitt.core.dto.websocket.data.OrderDataDto.OrderStatus.PARTIALLY_FILLED,
                                new BigDecimal("0.00100000"),
                                new BigDecimal("65010.00000000"),
                                BigDecimal.ZERO,
                                null,
                                30L,
                                1
                        ),
                        new MockOrderScenarioOverride.PlannedEvent(
                                com.marmitt.core.dto.websocket.data.OrderDataDto.OrderStatus.FILLED,
                                new BigDecimal("0.00200000"),
                                new BigDecimal("65020.00000000"),
                                BigDecimal.ZERO,
                                null,
                                30L,
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

        Transaction filled = awaitTransactionStatus(pendingBuy.getId(), TransactionStatus.FILLED, WAIT_TIMEOUT);
        assertEquals(0, filled.getEffectiveExecutedQuantity().compareTo(quantity));

        PositionRow position = awaitOpenPositionByOpenedByTransactionId(pendingBuy.getId(), WAIT_TIMEOUT);
        assertNotNull(position);
        assertEquals(0, position.quantity().compareTo(quantity),
                "Duplicate PARTIAL must not double-apply quantity");

        Integer countMatches = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transaction_matches WHERE buy_transaction_id = ? OR sell_transaction_id = ?",
                Integer.class,
                pendingBuy.getId(),
                pendingBuy.getId()
        );
        assertEquals(0, countMatches);
    }

    @Test
    void nearSimultaneousPartialAndFilledShouldConvergeWithoutQuantityDrift() {
        UUID portfolioId = createPortfolio();
        StrategyRunner runner = createAndActivateRunner(portfolioId);
        UUID runnerId = runner.getId();

        BigDecimal quantity = new BigDecimal("0.00200000");
        BigDecimal price = new BigDecimal("65100.00000000");
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
                SCENARIO_PARTIAL_FILLED_CONVERGENCE,
                null
        );
        strategyRunnerRepository.saveTransaction(pendingBuy);
        assertNoInitialPositionOrMatch(pendingBuy.getId());

        MockExchangeAdapter mockExchangeAdapter = getMockExchangeAdapter();
        mockExchangeAdapter.registerOrderScenarioOverride(clientOrderId, new MockOrderScenarioOverride(
                List.of(
                        new MockOrderScenarioOverride.PlannedEvent(
                                com.marmitt.core.dto.websocket.data.OrderDataDto.OrderStatus.PARTIALLY_FILLED,
                                new BigDecimal("0.00100000"),
                                new BigDecimal("65110.00000000"),
                                BigDecimal.ZERO,
                                null,
                                0L,
                                0
                        ),
                        new MockOrderScenarioOverride.PlannedEvent(
                                com.marmitt.core.dto.websocket.data.OrderDataDto.OrderStatus.FILLED,
                                new BigDecimal("0.00200000"),
                                new BigDecimal("65120.00000000"),
                                BigDecimal.ZERO,
                                null,
                                100L,
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

        Transaction filled = awaitTransactionStatus(pendingBuy.getId(), TransactionStatus.FILLED, WAIT_TIMEOUT);
        assertEquals(0, filled.getEffectiveExecutedQuantity().compareTo(quantity));

        PositionRow position = awaitOpenPositionByOpenedByTransactionId(pendingBuy.getId(), WAIT_TIMEOUT);
        assertNotNull(position);
        assertEquals(0, position.quantity().compareTo(quantity),
                "Near-simultaneous PARTIAL+FILLED must converge to exact filled quantity");

        Integer openRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM positions WHERE opened_by_transaction_id = ? AND status = 'OPEN'",
                Integer.class,
                pendingBuy.getId()
        );
        assertEquals(1, openRows);

        Integer countMatches = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transaction_matches WHERE buy_transaction_id = ? OR sell_transaction_id = ?",
                Integer.class,
                pendingBuy.getId(),
                pendingBuy.getId()
        );
        assertEquals(0, countMatches);
    }

    @Test
    void duplicateFilledShouldNotDoubleApplyEconomicEffects() {
        UUID portfolioId = createPortfolio();
        StrategyRunner runner = createAndActivateRunner(portfolioId);
        UUID runnerId = runner.getId();

        BigDecimal quantity = new BigDecimal("0.00200000");
        BigDecimal price = new BigDecimal("65200.00000000");
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
                SCENARIO_DUPLICATE_FILLED,
                null
        );
        strategyRunnerRepository.saveTransaction(pendingBuy);
        assertNoInitialPositionOrMatch(pendingBuy.getId());

        MockExchangeAdapter mockExchangeAdapter = getMockExchangeAdapter();
        mockExchangeAdapter.registerOrderScenarioOverride(clientOrderId, new MockOrderScenarioOverride(
                List.of(
                        new MockOrderScenarioOverride.PlannedEvent(
                                com.marmitt.core.dto.websocket.data.OrderDataDto.OrderStatus.FILLED,
                                new BigDecimal("0.00200000"),
                                new BigDecimal("65210.00000000"),
                                BigDecimal.ZERO,
                                null,
                                20L,
                                2
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

        BuyStateSnapshot stable = awaitStableFilledState(
                pendingBuy.getId(),
                WAIT_TIMEOUT,
                quantity
        );

        assertEquals(0, stable.executedQuantity().compareTo(quantity),
                "Duplicate FILLED must not change executed quantity after first finalization");
        assertNotNull(stable.positionQuantity());
        assertEquals(0, stable.positionQuantity().compareTo(quantity),
                "Duplicate FILLED must not increase position quantity more than once");
        assertEquals(1, stable.openRows());
        assertEquals(0, stable.matchCount());
    }

    @Test
    void duplicateSellFilledShouldNotDoubleApplyEconomicEffects() {
        UUID portfolioId = createPortfolio();
        StrategyRunner runner = createAndActivateRunner(portfolioId);
        UUID runnerId = runner.getId();

        BigDecimal quantity = new BigDecimal("0.00200000");
        BigDecimal buyPrice = new BigDecimal("65000.00000000");
        BigDecimal sellPrice = new BigDecimal("66000.00000000");
        BigDecimal buyCost = quantity.multiply(buyPrice);
        BigDecimal expectedPnl = quantity.multiply(sellPrice.subtract(buyPrice));

        String buyClientOrderId = ClientOrderId.generate(runner.getShortCode(), TransactionType.BUY);
        Transaction pendingBuy = new Transaction(
                runnerId,
                buyClientOrderId,
                TransactionType.BUY,
                SYMBOL,
                quantity,
                buyPrice,
                buyCost,
                new BigDecimal("0.90"),
                SCENARIO_DUPLICATE_SELL_FILLED + " setup buy",
                null
        );
        strategyRunnerRepository.saveTransaction(pendingBuy);
        assertTrue(globalBalanceRepository.reserveAtomic(portfolioId, buyCost),
                "Seeded BUY must reserve capital like ProcessTradeSignalUseCase would");
        assertNoInitialPositionOrMatch(pendingBuy.getId());

        MockExchangeAdapter mockExchangeAdapter = getMockExchangeAdapter();
        mockExchangeAdapter.registerOrderScenarioOverride(buyClientOrderId, new MockOrderScenarioOverride(
                List.of(
                        new MockOrderScenarioOverride.PlannedEvent(
                                com.marmitt.core.dto.websocket.data.OrderDataDto.OrderStatus.FILLED,
                                quantity,
                                buyPrice,
                                BigDecimal.ZERO,
                                null,
                                20L,
                                0
                        )
                ),
                MockOrderScenarioOverride.EventOrdering.AS_IS
        ));

        orderDispatchPort.dispatch(new OrderDispatchCommand(
                buyClientOrderId,
                runnerId,
                SYMBOL,
                MOCK_EXCHANGE,
                TransactionType.BUY,
                quantity,
                buyPrice
        ));

        BuyStateSnapshot buyState = awaitStableFilledState(pendingBuy.getId(), WAIT_TIMEOUT, quantity);
        assertEquals(0, buyState.positionQuantity().compareTo(quantity));

        PositionRow openedPosition = awaitOpenPositionByOpenedByTransactionId(pendingBuy.getId(), WAIT_TIMEOUT);
        String sellClientOrderId = ClientOrderId.generate(runner.getShortCode(), TransactionType.SELL);
        Transaction pendingSell = new Transaction(
                runnerId,
                sellClientOrderId,
                TransactionType.SELL,
                SYMBOL,
                quantity,
                sellPrice,
                quantity.multiply(sellPrice),
                new BigDecimal("0.90"),
                SCENARIO_DUPLICATE_SELL_FILLED,
                openedPosition.id()
        );
        strategyRunnerRepository.saveTransaction(pendingSell);
        assertEquals(0, countMatchesByTransactionId(pendingSell.getId()));
        assertTrue(strategyRunnerRepository.tryLockPositionForSell(openedPosition.id(), pendingSell.getId(), quantity),
                "Seeded SELL must lock the target position like ProcessTradeSignalUseCase would");

        mockExchangeAdapter.registerOrderScenarioOverride(sellClientOrderId, new MockOrderScenarioOverride(
                List.of(
                        new MockOrderScenarioOverride.PlannedEvent(
                                com.marmitt.core.dto.websocket.data.OrderDataDto.OrderStatus.FILLED,
                                quantity,
                                sellPrice,
                                BigDecimal.ZERO,
                                null,
                                20L,
                                2
                        )
                ),
                MockOrderScenarioOverride.EventOrdering.AS_IS
        ));

        orderDispatchPort.dispatch(new OrderDispatchCommand(
                sellClientOrderId,
                runnerId,
                SYMBOL,
                MOCK_EXCHANGE,
                TransactionType.SELL,
                quantity,
                sellPrice
        ));

        SellStateSnapshot stable = awaitStableSellState(
                portfolioId,
                pendingSell.getId(),
                openedPosition.id(),
                WAIT_TIMEOUT,
                quantity,
                expectedPnl
        );

        assertEquals(TransactionStatus.FILLED, stable.status());
        assertEquals(0, stable.executedQuantity().compareTo(quantity),
                "Duplicate SELL FILLED must not change executed quantity after first finalization");
        assertEquals(1, stable.matchCount(),
                "Duplicate SELL FILLED must persist exactly one transaction_match");
        assertEquals(0, stable.matchedQuantity().compareTo(quantity),
                "Duplicate SELL FILLED must not duplicate matched quantity");
        assertEquals(0, stable.pnlRealized().compareTo(expectedPnl),
                "Duplicate SELL FILLED must not duplicate realized PnL");
        assertEquals("CLOSED", stable.positionStatus());
        assertEquals(0, stable.positionQuantity().compareTo(BigDecimal.ZERO),
                "Duplicate SELL FILLED must not reduce position more than once");
        assertEquals(0, stable.realizedBalance().compareTo(expectedPnl),
                "Duplicate SELL FILLED must release capital and apply PnL exactly once");
        assertEquals(0, stable.availableBalance().compareTo(INITIAL_CAPITAL.add(expectedPnl)),
                "Duplicate SELL FILLED must not credit available balance twice");
        assertEquals(0, stable.reservedBalance().compareTo(BigDecimal.ZERO),
                "Duplicate SELL FILLED must release the reserved buy cost exactly once");
    }

    @Test
    void reorderedBuyFilledBeforePartialShouldIgnoreLatePartial() {
        UUID portfolioId = createPortfolio();
        StrategyRunner runner = createAndActivateRunner(portfolioId);
        UUID runnerId = runner.getId();

        BigDecimal quantity = new BigDecimal("0.00200000");
        BigDecimal price = new BigDecimal("65500.00000000");
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
                SCENARIO_REORDERED_BUY_FILLED_BEFORE_PARTIAL,
                null
        );
        strategyRunnerRepository.saveTransaction(pendingBuy);
        assertNoInitialPositionOrMatch(pendingBuy.getId());

        MockExchangeAdapter mockExchangeAdapter = getMockExchangeAdapter();
        mockExchangeAdapter.registerOrderScenarioOverride(clientOrderId, new MockOrderScenarioOverride(
                List.of(
                        new MockOrderScenarioOverride.PlannedEvent(
                                com.marmitt.core.dto.websocket.data.OrderDataDto.OrderStatus.PARTIALLY_FILLED,
                                new BigDecimal("0.00100000"),
                                new BigDecimal("65505.00000000"),
                                BigDecimal.ZERO,
                                null,
                                20L,
                                0
                        ),
                        new MockOrderScenarioOverride.PlannedEvent(
                                com.marmitt.core.dto.websocket.data.OrderDataDto.OrderStatus.FILLED,
                                quantity,
                                new BigDecimal("65510.00000000"),
                                BigDecimal.ZERO,
                                null,
                                20L,
                                0
                        )
                ),
                MockOrderScenarioOverride.EventOrdering.REVERSE
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

        BuyStateSnapshot stable = awaitStableFilledState(pendingBuy.getId(), WAIT_TIMEOUT, quantity);

        assertEquals(TransactionStatus.FILLED, stable.status());
        assertEquals(0, stable.executedQuantity().compareTo(quantity),
                "Late BUY PARTIAL after FILLED must not reduce executed quantity");
        assertEquals(1, stable.openRows(),
                "Late BUY PARTIAL after FILLED must not create another position");
        assertEquals(0, stable.positionQuantity().compareTo(quantity),
                "Late BUY PARTIAL after FILLED must not drift position quantity");
        assertEquals(0, stable.matchCount());
    }

    @Test
    void reorderedSellFilledBeforePartialShouldIgnoreLatePartial() {
        UUID portfolioId = createPortfolio();
        StrategyRunner runner = createAndActivateRunner(portfolioId);

        BigDecimal quantity = new BigDecimal("0.00200000");
        BigDecimal buyPrice = new BigDecimal("65000.00000000");
        BigDecimal sellPrice = new BigDecimal("66100.00000000");
        BigDecimal expectedPnl = quantity.multiply(sellPrice.subtract(buyPrice));

        SellSetup sellSetup = createFilledBuyAndLockedSell(
                portfolioId,
                runner,
                quantity,
                buyPrice,
                sellPrice,
                SCENARIO_REORDERED_SELL_FILLED_BEFORE_PARTIAL
        );

        MockExchangeAdapter mockExchangeAdapter = getMockExchangeAdapter();
        mockExchangeAdapter.registerOrderScenarioOverride(
                sellSetup.sellTransaction().getClientOrderId(),
                new MockOrderScenarioOverride(
                        List.of(
                                new MockOrderScenarioOverride.PlannedEvent(
                                        com.marmitt.core.dto.websocket.data.OrderDataDto.OrderStatus.PARTIALLY_FILLED,
                                        new BigDecimal("0.00100000"),
                                        new BigDecimal("66090.00000000"),
                                        BigDecimal.ZERO,
                                        null,
                                        20L,
                                        0
                                ),
                                new MockOrderScenarioOverride.PlannedEvent(
                                        com.marmitt.core.dto.websocket.data.OrderDataDto.OrderStatus.FILLED,
                                        quantity,
                                        sellPrice,
                                        BigDecimal.ZERO,
                                        null,
                                        20L,
                                        0
                                )
                        ),
                        MockOrderScenarioOverride.EventOrdering.REVERSE
                )
        );

        orderDispatchPort.dispatch(new OrderDispatchCommand(
                sellSetup.sellTransaction().getClientOrderId(),
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

        assertEquals(TransactionStatus.FILLED, stable.status());
        assertEquals(0, stable.executedQuantity().compareTo(quantity),
                "Late SELL PARTIAL after FILLED must not reduce executed quantity");
        assertEquals(1, stable.matchCount(),
                "Late SELL PARTIAL after FILLED must not create another transaction_match");
        assertEquals(0, stable.matchedQuantity().compareTo(quantity),
                "Late SELL PARTIAL after FILLED must not drift matched quantity");
        assertEquals(0, stable.pnlRealized().compareTo(expectedPnl),
                "Late SELL PARTIAL after FILLED must not change realized PnL");
        assertEquals("CLOSED", stable.positionStatus());
        assertEquals(0, stable.positionQuantity().compareTo(BigDecimal.ZERO));
        assertEquals(0, stable.realizedBalance().compareTo(expectedPnl));
        assertEquals(0, stable.availableBalance().compareTo(INITIAL_CAPITAL.add(expectedPnl)));
        assertEquals(0, stable.reservedBalance().compareTo(BigDecimal.ZERO));
    }

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

    private MockExchangeAdapter getMockExchangeAdapter() {
        return exchangeAdapterRepository.findStreamingByName(MOCK_EXCHANGE)
                .filter(MockExchangeAdapter.class::isInstance)
                .map(MockExchangeAdapter.class::cast)
                .orElseThrow(() -> new IllegalStateException(
                        MOCK_EXCHANGE + " adapter not found or has invalid type"));
    }

    private UUID createPortfolio() {
        CreatePortfolioResponse response = createPortfolioPort.execute(
                CreatePortfolioRequest.builder()
                        .name("phase1b-det-" + UUID.randomUUID())
                        .initialCapitalAmount(INITIAL_CAPITAL)
                        .currency("USDT")
                        .build()
        );
        assertNotNull(response.portfolioId(), "Portfolio creation failed: " + response.message());
        return response.portfolioId();
    }

    private StrategyRunner createAndActivateRunner(UUID portfolioId) {
        CreateRunnerResponse response = createRunnerPort.execute(
                CreateRunnerRequest.builder()
                        .portfolioId(portfolioId)
                        .strategyId(SMA_STRATEGY_ID)
                        .symbol(SYMBOL)
                        .exchangeName(MOCK_EXCHANGE)
                        .allowedMarketDataSources(Set.of("BINANCE"))
                        .build()
        );
        assertNotNull(response.runnerId(), "Runner creation failed: " + response.message());

        StrategyRunner runner = strategyRunnerRepository.findById(response.runnerId())
                .orElseThrow(() -> new IllegalStateException("Runner not found: " + response.runnerId()));
        runner.startInitializing();
        runner.activate();
        strategyRunnerRepository.save(runner);

        return runner;
    }

    private SellSetup createFilledBuyAndLockedSell(UUID portfolioId,
                                                   StrategyRunner runner,
                                                   BigDecimal quantity,
                                                   BigDecimal buyPrice,
                                                   BigDecimal sellPrice,
                                                   String reason) {
        BigDecimal buyCost = quantity.multiply(buyPrice);
        String buyClientOrderId = ClientOrderId.generate(runner.getShortCode(), TransactionType.BUY);
        Transaction pendingBuy = new Transaction(
                runner.getId(),
                buyClientOrderId,
                TransactionType.BUY,
                SYMBOL,
                quantity,
                buyPrice,
                buyCost,
                new BigDecimal("0.90"),
                reason + " setup buy",
                null
        );
        strategyRunnerRepository.saveTransaction(pendingBuy);
        assertTrue(globalBalanceRepository.reserveAtomic(portfolioId, buyCost),
                "Seeded BUY must reserve capital like ProcessTradeSignalUseCase would");
        assertNoInitialPositionOrMatch(pendingBuy.getId());

        MockExchangeAdapter mockExchangeAdapter = getMockExchangeAdapter();
        mockExchangeAdapter.registerOrderScenarioOverride(buyClientOrderId, new MockOrderScenarioOverride(
                List.of(
                        new MockOrderScenarioOverride.PlannedEvent(
                                com.marmitt.core.dto.websocket.data.OrderDataDto.OrderStatus.FILLED,
                                quantity,
                                buyPrice,
                                BigDecimal.ZERO,
                                null,
                                20L,
                                0
                        )
                ),
                MockOrderScenarioOverride.EventOrdering.AS_IS
        ));

        orderDispatchPort.dispatch(new OrderDispatchCommand(
                buyClientOrderId,
                runner.getId(),
                SYMBOL,
                MOCK_EXCHANGE,
                TransactionType.BUY,
                quantity,
                buyPrice
        ));

        BuyStateSnapshot buyState = awaitStableFilledState(pendingBuy.getId(), WAIT_TIMEOUT, quantity);
        assertEquals(0, buyState.positionQuantity().compareTo(quantity));

        PositionRow openedPosition = awaitOpenPositionByOpenedByTransactionId(pendingBuy.getId(), WAIT_TIMEOUT);
        String sellClientOrderId = ClientOrderId.generate(runner.getShortCode(), TransactionType.SELL);
        Transaction pendingSell = new Transaction(
                runner.getId(),
                sellClientOrderId,
                TransactionType.SELL,
                SYMBOL,
                quantity,
                sellPrice,
                quantity.multiply(sellPrice),
                new BigDecimal("0.90"),
                reason,
                openedPosition.id()
        );
        strategyRunnerRepository.saveTransaction(pendingSell);
        assertEquals(0, countMatchesByTransactionId(pendingSell.getId()));
        assertTrue(strategyRunnerRepository.tryLockPositionForSell(openedPosition.id(), pendingSell.getId(), quantity),
                "Seeded SELL must lock the target position like ProcessTradeSignalUseCase would");

        return new SellSetup(pendingBuy, pendingSell, openedPosition);
    }

    private Transaction awaitTransactionStatus(UUID transactionId,
                                               TransactionStatus expectedStatus,
                                               Duration timeout) {
        return awaitCondition(
                timeout,
                DEFAULT_POLL_INTERVAL_MS,
                () -> strategyRunnerRepository.findTransactionById(transactionId).orElse(null),
                tx -> tx != null && tx.getStatus() == expectedStatus,
                "Timeout waiting transaction status " + expectedStatus + " for transactionId=" + transactionId
        );
    }

    private PositionRow awaitOpenPositionByOpenedByTransactionId(UUID openedByTransactionId, Duration timeout) {
        return awaitCondition(
                timeout,
                DEFAULT_POLL_INTERVAL_MS,
                () -> findOpenPositionByOpenedByTransactionId(openedByTransactionId),
                position -> position != null,
                "Timeout waiting OPEN position for openedByTransactionId=" + openedByTransactionId
        );
    }

    private PositionRow findOpenPositionByOpenedByTransactionId(UUID openedByTransactionId) {
        List<PositionRow> rows = jdbcTemplate.query(
                """
                        SELECT id, quantity
                          FROM positions
                         WHERE opened_by_transaction_id = ?
                           AND status = 'OPEN'
                         LIMIT 1
                        """,
                (rs, rowNum) -> new PositionRow(
                        UUID.fromString(rs.getString("id")),
                        rs.getBigDecimal("quantity")
                ),
                openedByTransactionId
        );
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private int countOpenPositionsByOpenedByTransactionId(UUID openedByTransactionId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM positions WHERE opened_by_transaction_id = ? AND status = 'OPEN'",
                Integer.class,
                openedByTransactionId
        );
        return count == null ? 0 : count;
    }

    private int countMatchesByTransactionId(UUID transactionId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transaction_matches WHERE buy_transaction_id = ? OR sell_transaction_id = ?",
                Integer.class,
                transactionId,
                transactionId
        );
        return count == null ? 0 : count;
    }

    private BalanceRow readBalance(UUID portfolioId) {
        List<BalanceRow> rows = jdbcTemplate.query(
                """
                        SELECT available_balance, reserved_balance
                              , realized_balance
                          FROM global_balances
                         WHERE portfolio_id = ?
                        """,
                (rs, rowNum) -> new BalanceRow(
                        rs.getBigDecimal("available_balance"),
                        rs.getBigDecimal("reserved_balance"),
                        rs.getBigDecimal("realized_balance")
                ),
                portfolioId
        );
        if (rows.isEmpty()) {
            throw new IllegalStateException("Balance not found for portfolioId=" + portfolioId);
        }
        return rows.getFirst();
    }

    private void awaitBalanceState(UUID portfolioId,
                                   BigDecimal expectedAvailable,
                                   BigDecimal expectedReserved,
                                   Duration timeout) {
        awaitCondition(
                timeout,
                DEFAULT_POLL_INTERVAL_MS,
                () -> readBalance(portfolioId),
                balance -> balance.available().compareTo(expectedAvailable) == 0
                        && balance.reserved().compareTo(expectedReserved) == 0,
                "Timeout waiting balance state for portfolioId=" + portfolioId
                        + " expectedAvailable=" + expectedAvailable
                        + " expectedReserved=" + expectedReserved
        );
    }

    private BuyStateSnapshot awaitStableFilledState(UUID openedByTransactionId,
                                                    Duration timeout,
                                                    BigDecimal expectedQuantity) {
        Instant deadline = Instant.now().plus(timeout);
        BuyStateSnapshot converged = null;
        while (Instant.now().isBefore(deadline)) {
            BuyStateSnapshot current = readBuyState(openedByTransactionId);
            if (converged == null) {
                if (isExpectedFilledState(current, expectedQuantity)) {
                    converged = current;
                }
            } else if (!isExpectedFilledState(current, expectedQuantity)) {
                throw new AssertionError("Filled buy state drift detected after convergence for openedByTransactionId="
                        + openedByTransactionId + " current=" + current);
            }
            sleep(FILLED_STABILITY_POLL_INTERVAL_MS);
        }
        if (converged == null) {
            throw new AssertionError("Timeout waiting FILLED buy convergence for openedByTransactionId="
                    + openedByTransactionId);
        }
        return converged;
    }

    private SellStateSnapshot awaitStableSellState(UUID portfolioId,
                                                   UUID sellTransactionId,
                                                   UUID positionId,
                                                   Duration timeout,
                                                   BigDecimal expectedQuantity,
                                                   BigDecimal expectedPnl) {
        Instant deadline = Instant.now().plus(timeout);
        SellStateSnapshot converged = null;
        while (Instant.now().isBefore(deadline)) {
            SellStateSnapshot current = readSellState(portfolioId, sellTransactionId, positionId);
            if (converged == null) {
                if (isExpectedSellFilledState(current, expectedQuantity, expectedPnl)) {
                    converged = current;
                }
            } else if (!isExpectedSellFilledState(current, expectedQuantity, expectedPnl)) {
                throw new AssertionError("Filled sell state drift detected after convergence for sellTransactionId="
                        + sellTransactionId + " current=" + current);
            }
            sleep(FILLED_STABILITY_POLL_INTERVAL_MS);
        }
        if (converged == null) {
            throw new AssertionError("Timeout waiting FILLED sell convergence for sellTransactionId="
                    + sellTransactionId);
        }
        return converged;
    }

    private BuyStateSnapshot readBuyState(UUID openedByTransactionId) {
        Transaction tx = strategyRunnerRepository.findTransactionById(openedByTransactionId)
                .orElseThrow(() -> new IllegalStateException("Transaction not found: " + openedByTransactionId));
        PositionRow position = findOpenPositionByOpenedByTransactionId(openedByTransactionId);
        Integer openRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM positions WHERE opened_by_transaction_id = ? AND status = 'OPEN'",
                Integer.class,
                openedByTransactionId
        );
        Integer matchCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transaction_matches WHERE buy_transaction_id = ? OR sell_transaction_id = ?",
                Integer.class,
                openedByTransactionId,
                openedByTransactionId
        );
        return new BuyStateSnapshot(
                tx.getStatus(),
                tx.getEffectiveExecutedQuantity(),
                tx.getVersion(),
                position == null ? null : position.quantity(),
                openRows == null ? 0 : openRows,
                matchCount == null ? 0 : matchCount
        );
    }

    private SellStateSnapshot readSellState(UUID portfolioId, UUID sellTransactionId, UUID positionId) {
        Transaction tx = strategyRunnerRepository.findTransactionById(sellTransactionId)
                .orElseThrow(() -> new IllegalStateException("Transaction not found: " + sellTransactionId));
        PositionLifecycleRow position = findPositionById(positionId);
        MatchAggregateRow matches = readMatchAggregateBySellTransactionId(sellTransactionId);
        BalanceRow balance = readBalance(portfolioId);
        return new SellStateSnapshot(
                tx.getStatus(),
                tx.getEffectiveExecutedQuantity(),
                tx.getVersion(),
                position.status(),
                position.quantity(),
                position.lockedByTransactionId(),
                matches.matchCount(),
                matches.matchedQuantity(),
                matches.pnlRealized(),
                balance.available(),
                balance.reserved(),
                balance.realized()
        );
    }

    private PositionLifecycleRow findPositionById(UUID positionId) {
        List<PositionLifecycleRow> rows = jdbcTemplate.query(
                """
                        SELECT status, quantity, locked_by_transaction_id
                          FROM positions
                         WHERE id = ?
                        """,
                (rs, rowNum) -> new PositionLifecycleRow(
                        rs.getString("status"),
                        rs.getBigDecimal("quantity"),
                        rs.getObject("locked_by_transaction_id", UUID.class)
                ),
                positionId
        );
        if (rows.isEmpty()) {
            throw new IllegalStateException("Position not found: " + positionId);
        }
        return rows.getFirst();
    }

    private MatchAggregateRow readMatchAggregateBySellTransactionId(UUID sellTransactionId) {
        return jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*) AS match_count,
                               COALESCE(SUM(matched_quantity), 0) AS matched_quantity,
                               COALESCE(SUM(pnl_realized), 0) AS pnl_realized
                          FROM transaction_matches
                         WHERE sell_transaction_id = ?
                        """,
                (rs, rowNum) -> new MatchAggregateRow(
                        rs.getInt("match_count"),
                        rs.getBigDecimal("matched_quantity"),
                        rs.getBigDecimal("pnl_realized")
                ),
                sellTransactionId
        );
    }

    private void assertNoInitialPositionOrMatch(UUID transactionId) {
        assertEquals(0, countOpenPositionsByOpenedByTransactionId(transactionId),
                "Initial state must not have OPEN position for transactionId=" + transactionId);
        assertEquals(0, countMatchesByTransactionId(transactionId),
                "Initial state must not have transaction_matches for transactionId=" + transactionId);
    }

    private static boolean isExpectedFilledState(BuyStateSnapshot snapshot, BigDecimal expectedQuantity) {
        return snapshot.status() == TransactionStatus.FILLED
                && isSameValue(snapshot.executedQuantity(), expectedQuantity)
                && isSameValue(snapshot.positionQuantity(), expectedQuantity)
                && snapshot.openRows() == 1
                && snapshot.matchCount() == 0;
    }

    private static boolean isExpectedSellFilledState(SellStateSnapshot snapshot,
                                                     BigDecimal expectedQuantity,
                                                     BigDecimal expectedPnl) {
        return snapshot.status() == TransactionStatus.FILLED
                && isSameValue(snapshot.executedQuantity(), expectedQuantity)
                && "CLOSED".equals(snapshot.positionStatus())
                && isSameValue(snapshot.positionQuantity(), BigDecimal.ZERO)
                && snapshot.lockedByTransactionId() == null
                && snapshot.matchCount() == 1
                && isSameValue(snapshot.matchedQuantity(), expectedQuantity)
                && isSameValue(snapshot.pnlRealized(), expectedPnl)
                && isSameValue(snapshot.reservedBalance(), BigDecimal.ZERO)
                && isSameValue(snapshot.realizedBalance(), expectedPnl)
                && isSameValue(snapshot.availableBalance(), INITIAL_CAPITAL.add(expectedPnl));
    }

    private static boolean isSameValue(BigDecimal left, BigDecimal right) {
        if (left == null && right == null) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        return left.compareTo(right) == 0;
    }

    private <T> T awaitCondition(Duration timeout,
                                 long pollIntervalMs,
                                 Supplier<T> stateSupplier,
                                 Predicate<T> isSatisfied,
                                 String timeoutMessage) {
        Instant deadline = Instant.now().plus(timeout);
        T last = null;
        while (Instant.now().isBefore(deadline)) {
            last = stateSupplier.get();
            if (isSatisfied.test(last)) {
                return last;
            }
            sleep(pollIntervalMs);
        }
        throw new AssertionError(timeoutMessage + " last=" + last);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting async processing", e);
        }
    }

    private record PositionRow(UUID id, BigDecimal quantity) {
    }

    private record PositionLifecycleRow(String status,
                                        BigDecimal quantity,
                                        UUID lockedByTransactionId) {
    }

    private record MatchAggregateRow(int matchCount,
                                     BigDecimal matchedQuantity,
                                     BigDecimal pnlRealized) {
    }

    private record SellSetup(Transaction buyTransaction,
                             Transaction sellTransaction,
                             PositionRow position) {
    }

    private record BuyStateSnapshot(TransactionStatus status,
                                    BigDecimal executedQuantity,
                                    Long version,
                                    BigDecimal positionQuantity,
                                    int openRows,
                                    int matchCount) {
    }

    private record SellStateSnapshot(TransactionStatus status,
                                     BigDecimal executedQuantity,
                                     Long version,
                                     String positionStatus,
                                     BigDecimal positionQuantity,
                                     UUID lockedByTransactionId,
                                     int matchCount,
                                     BigDecimal matchedQuantity,
                                     BigDecimal pnlRealized,
                                     BigDecimal availableBalance,
                                     BigDecimal reservedBalance,
                                     BigDecimal realizedBalance) {
    }

    private record BalanceRow(BigDecimal available, BigDecimal reserved, BigDecimal realized) {
    }
}
