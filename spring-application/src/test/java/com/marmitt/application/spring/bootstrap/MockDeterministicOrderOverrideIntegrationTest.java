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
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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
    private static final Duration WAIT_TIMEOUT = Duration.ofSeconds(10);

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
    void deterministicOverrideShouldConvergeWithoutDoubleApplyingDuplicatePartial() {
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
                "phase1b deterministic override",
                null
        );
        strategyRunnerRepository.saveTransaction(pendingBuy);

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
                "MOCK",
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
                "phase1b partial+filled convergence",
                null
        );
        strategyRunnerRepository.saveTransaction(pendingBuy);

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
                "MOCK",
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
                "phase1b duplicate filled idempotency",
                null
        );
        strategyRunnerRepository.saveTransaction(pendingBuy);

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
                "MOCK",
                TransactionType.BUY,
                quantity,
                price
        ));

        BuyStateSnapshot stable = awaitStableFilledBuyState(
                pendingBuy.getId(),
                WAIT_TIMEOUT,
                Duration.ofMillis(400)
        );

        assertEquals(0, stable.executedQuantity().compareTo(quantity),
                "Duplicate FILLED must not change executed quantity after first finalization");
        assertNotNull(stable.positionQuantity());
        assertEquals(0, stable.positionQuantity().compareTo(quantity),
                "Duplicate FILLED must not increase position quantity more than once");
        assertEquals(1, stable.openRows());
        assertEquals(0, stable.matchCount());
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
                        .initialCapitalAmount(new BigDecimal("10000.00"))
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
                        .exchangeName("MOCK")
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
            sleep(80);
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
            sleep(80);
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

    private BuyStateSnapshot awaitStableFilledBuyState(UUID openedByTransactionId,
                                                       Duration timeout,
                                                       Duration stableWindow) {
        Instant deadline = Instant.now().plus(timeout);
        BuyStateSnapshot previous = null;
        Instant stableSince = null;
        while (Instant.now().isBefore(deadline)) {
            BuyStateSnapshot current = readBuyState(openedByTransactionId);
            if (current.status() == TransactionStatus.FILLED && current.positionQuantity() != null) {
                if (previous != null && sameState(previous, current)) {
                    if (stableSince == null) {
                        stableSince = Instant.now();
                    }
                    if (!Instant.now().isBefore(stableSince.plus(stableWindow))) {
                        return current;
                    }
                } else {
                    stableSince = Instant.now();
                }
            } else {
                stableSince = null;
            }
            previous = current;
            sleep(50);
        }
        throw new AssertionError("Timeout waiting stable FILLED state for openedByTransactionId=" + openedByTransactionId);
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

    private static boolean sameState(BuyStateSnapshot left, BuyStateSnapshot right) {
        return left.status() == right.status()
                && compareNullable(left.executedQuantity(), right.executedQuantity()) == 0
                && compareNullable(left.positionQuantity(), right.positionQuantity()) == 0
                && Objects.equals(left.version(), right.version())
                && left.openRows() == right.openRows()
                && left.matchCount() == right.matchCount();
    }

    private static int compareNullable(BigDecimal left, BigDecimal right) {
        if (left == null && right == null) {
            return 0;
        }
        if (left == null || right == null) {
            return 1;
        }
        return left.compareTo(right);
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
}
