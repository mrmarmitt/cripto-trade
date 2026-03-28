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
        Object adapter = exchangeAdapterRepository.findStreamingByName("MOCK")
                .orElseThrow(() -> new IllegalStateException("MOCK adapter not found"));
        if (!(adapter instanceof MockExchangeAdapter mock)) {
            throw new IllegalStateException("MOCK adapter has unexpected type: " + adapter.getClass().getName());
        }
        return mock;
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

    private Transaction awaitTransactionStatus(UUID transactionId,
                                               TransactionStatus expectedStatus,
                                               Duration timeout) {
        Instant deadline = Instant.now().plus(timeout);
        Transaction last = null;
        while (Instant.now().isBefore(deadline)) {
            last = strategyRunnerRepository.findTransactionById(transactionId).orElse(null);
            if (last != null && last.getStatus() == expectedStatus) {
                return last;
            }
            sleep(DEFAULT_POLL_INTERVAL_MS);
        }
        throw new AssertionError("Timeout waiting transaction status " + expectedStatus
                + " for transactionId=" + transactionId + " last=" + (last == null ? "null" : last.getStatus()));
    }

    private PositionRow awaitOpenPositionByOpenedByTransactionId(UUID openedByTransactionId, Duration timeout) {
        Instant deadline = Instant.now().plus(timeout);
        PositionRow last = null;
        while (Instant.now().isBefore(deadline)) {
            last = findOpenPositionByOpenedByTransactionId(openedByTransactionId);
            if (last != null) {
                return last;
            }
            sleep(DEFAULT_POLL_INTERVAL_MS);
        }
        throw new AssertionError("Timeout waiting OPEN position for openedByTransactionId="
                + openedByTransactionId + " last=" + last);
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
                          FROM global_balances
                         WHERE portfolio_id = ?
                        """,
                (rs, rowNum) -> new BalanceRow(
                        rs.getBigDecimal("available_balance"),
                        rs.getBigDecimal("reserved_balance")
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
        Instant deadline = Instant.now().plus(timeout);
        BalanceRow last = null;
        while (Instant.now().isBefore(deadline)) {
            last = readBalance(portfolioId);
            if (last.available().compareTo(expectedAvailable) == 0
                    && last.reserved().compareTo(expectedReserved) == 0) {
                return;
            }
            sleep(DEFAULT_POLL_INTERVAL_MS);
        }
        throw new AssertionError("Timeout waiting balance state for portfolioId=" + portfolioId
                + " expectedAvailable=" + expectedAvailable
                + " expectedReserved=" + expectedReserved
                + " last=" + last);
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

    private static boolean isSameValue(BigDecimal left, BigDecimal right) {
        if (left == null && right == null) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        return left.compareTo(right) == 0;
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

    private record BuyStateSnapshot(TransactionStatus status,
                                    BigDecimal executedQuantity,
                                    Long version,
                                    BigDecimal positionQuantity,
                                    int openRows,
                                    int matchCount) {
    }

    private record BalanceRow(BigDecimal available, BigDecimal reserved) {
    }
}
