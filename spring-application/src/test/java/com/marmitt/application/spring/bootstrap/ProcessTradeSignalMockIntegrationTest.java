package com.marmitt.application.spring.bootstrap;

import com.marmitt.application.spring.CTradeApplication;
import com.marmitt.core.domain.Symbol;
import com.marmitt.core.domain.runner.StrategyRunner;
import com.marmitt.core.dto.portfolio.request.CreatePortfolioRequest;
import com.marmitt.core.dto.portfolio.response.CreatePortfolioResponse;
import com.marmitt.core.dto.runner.CreateRunnerRequest;
import com.marmitt.core.dto.runner.CreateRunnerResponse;
import com.marmitt.core.dto.websocket.data.MarketDataDto;
import com.marmitt.core.ports.inbound.portfolio.CreatePortfolioPort;
import com.marmitt.core.ports.inbound.runner.CreateRunnerPort;
import com.marmitt.core.ports.inbound.runner.ProcessTradeSignalPort;
import com.marmitt.core.ports.outbound.repository.StrategyRunnerRepositoryPort;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@Testcontainers
@SpringBootTest(
        classes = CTradeApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "runner.boot.orchestrator-enabled=false"
        }
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ProcessTradeSignalMockIntegrationTest {

    private static final UUID SMA_STRATEGY_ID =
            UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
    private static final String SYMBOL = "BTCUSDT";
    private static final String MARKET_DATA_EXCHANGE = "BINANCE";
    private static final Duration WAIT_TIMEOUT = Duration.ofSeconds(20);

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
    private ProcessTradeSignalPort processTradeSignalPort;

    @Autowired
    private StrategyRunnerRepositoryPort strategyRunnerRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("TRUNCATE TABLE portfolios CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE capital_event_ledger");
    }

    @Test
    void buySignalShouldCreateAndFillTransactionAndOpenPosition() {
        UUID portfolioId = createPortfolio();
        UUID runnerId = createAndActivateRunner(portfolioId);

        publishBuyTriggerTicks();

        BuyLifecycleSnapshot lifecycle = awaitBuyLifecycle(runnerId, WAIT_TIMEOUT);
        TransactionSnapshot filled = lifecycle.transaction();
        assertEquals("FILLED", filled.status());
        assertNotNull(filled.exchangeOrderId());
        assertTrue(filled.executedQuantity().compareTo(BigDecimal.ZERO) > 0);
        assertEquals(0, filled.quantity().compareTo(filled.executedQuantity()));

        PositionSnapshot position = latestOpenPosition(runnerId, SYMBOL);
        assertNotNull(position, "Expected OPEN position after BUY fill");
        assertEquals(filled.id(), position.openedByTransactionId());
        assertTrue(position.quantity().compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    void buyOrderShouldPassThroughPartialAndThenFill() {
        UUID portfolioId = createPortfolio();
        UUID runnerId = createAndActivateRunner(portfolioId);

        publishBuyTriggerTicks();

        BuyLifecycleSnapshot lifecycle = awaitBuyLifecycle(runnerId, WAIT_TIMEOUT);
        TransactionSnapshot filled = lifecycle.transaction();
        assertEquals("FILLED", filled.status());
        assertTrue(
                lifecycle.seenStatuses().contains("PARTIAL")
                        || lifecycle.seenStatuses().contains("PARTIALLY_FILLED"),
                "Expected at least one PARTIAL transition before FILLED. Seen: " + lifecycle.seenStatuses()
        );
        assertTrue(filled.version() >= 3L,
                "Expected multiple transaction updates (SUBMITTED/PARTIAL/FILLED). version=" + filled.version());
    }

    private UUID createPortfolio() {
        CreatePortfolioResponse response = createPortfolioPort.execute(
                CreatePortfolioRequest.builder()
                        .name("phase1a-" + UUID.randomUUID())
                        .initialCapitalAmount(new BigDecimal("10000.00"))
                        .currency("USDT")
                        .build()
        );
        assertNotNull(response.portfolioId(), "Portfolio creation failed: " + response.message());
        return response.portfolioId();
    }

    private UUID createAndActivateRunner(UUID portfolioId) {
        CreateRunnerResponse response = createRunnerPort.execute(
                CreateRunnerRequest.builder()
                        .portfolioId(portfolioId)
                        .strategyId(SMA_STRATEGY_ID)
                        .symbol(SYMBOL)
                        .exchangeName("MOCK")
                        .allowedMarketDataSources(Set.of(MARKET_DATA_EXCHANGE))
                        .build()
        );
        assertNotNull(response.runnerId(), "Runner creation failed: " + response.message());

        StrategyRunner runner = strategyRunnerRepository.findById(response.runnerId())
                .orElseThrow(() -> new IllegalStateException("Runner not found: " + response.runnerId()));

        runner.startInitializing();
        runner.activate();
        strategyRunnerRepository.save(runner);

        return runner.getId();
    }

    private void publishBuyTriggerTicks() {
        Instant now = Instant.now();
        processTradeSignalPort.execute(newMarketData(new BigDecimal("65000.00"), now));
        processTradeSignalPort.execute(newMarketData(new BigDecimal("65100.00"), now.plusMillis(200)));
    }

    private MarketDataDto newMarketData(BigDecimal price, Instant timestamp) {
        return new MarketDataDto(
                MARKET_DATA_EXCHANGE,
                Symbol.of(SYMBOL),
                price,
                price,
                price,
                new BigDecimal("1000"),
                price,
                price,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                timestamp
        );
    }

    private BuyLifecycleSnapshot awaitBuyLifecycle(UUID runnerId, Duration timeout) {
        Instant deadline = Instant.now().plus(timeout);
        LinkedHashSet<String> seenStatuses = new LinkedHashSet<>();
        TransactionSnapshot lastSeen = null;

        while (Instant.now().isBefore(deadline)) {
            TransactionSnapshot tx = latestBuyTransaction(runnerId);
            if (tx != null) {
                lastSeen = tx;
                seenStatuses.add(tx.status());
                if ("FILLED".equals(tx.status())) {
                    return new BuyLifecycleSnapshot(tx, Set.copyOf(seenStatuses));
                }
                if ("REJECTED".equals(tx.status()) || "CANCELED".equals(tx.status()) || "EXPIRED".equals(tx.status())) {
                    fail("Expected FILLED but transaction ended with " + tx.status() + " tx=" + tx);
                }
            }
            sleep(60);
        }

        fail("Timeout waiting BUY lifecycle. Last tx=" + lastSeen + ", seenStatuses=" + seenStatuses);
        return null;
    }

    private TransactionSnapshot latestBuyTransaction(UUID runnerId) {
        List<TransactionSnapshot> results = jdbcTemplate.query(
                """
                        SELECT id,
                               status,
                               quantity,
                               executed_quantity,
                               exchange_order_id,
                               version
                          FROM transactions
                         WHERE runner_id = ?
                           AND type = 'BUY'
                         ORDER BY requested_at DESC
                         LIMIT 1
                        """,
                (rs, rowNum) -> new TransactionSnapshot(
                        UUID.fromString(rs.getString("id")),
                        rs.getString("status"),
                        rs.getBigDecimal("quantity"),
                        rs.getBigDecimal("executed_quantity"),
                        rs.getString("exchange_order_id"),
                        rs.getLong("version")
                ),
                runnerId
        );
        return results.isEmpty() ? null : results.getFirst();
    }

    private PositionSnapshot latestOpenPosition(UUID runnerId, String symbol) {
        List<PositionSnapshot> results = jdbcTemplate.query(
                """
                        SELECT id, runner_id, symbol, status, quantity, opened_by_transaction_id
                          FROM positions
                         WHERE runner_id = ?
                           AND UPPER(symbol) = UPPER(?)
                           AND status = 'OPEN'
                         ORDER BY opened_at DESC
                         LIMIT 1
                        """,
                (rs, rowNum) -> new PositionSnapshot(
                        UUID.fromString(rs.getString("id")),
                        UUID.fromString(rs.getString("runner_id")),
                        rs.getString("symbol"),
                        rs.getString("status"),
                        rs.getBigDecimal("quantity"),
                        UUID.fromString(rs.getString("opened_by_transaction_id"))
                ),
                runnerId,
                symbol
        );
        return results.isEmpty() ? null : results.getFirst();
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting async processing", e);
        }
    }

    private record TransactionSnapshot(UUID id,
                                       String status,
                                       BigDecimal quantity,
                                       BigDecimal executedQuantity,
                                       String exchangeOrderId,
                                       long version) {
    }

    private record PositionSnapshot(UUID id,
                                    UUID runnerId,
                                    String symbol,
                                    String status,
                                    BigDecimal quantity,
                                    UUID openedByTransactionId) {
    }

    private record BuyLifecycleSnapshot(TransactionSnapshot transaction,
                                        Set<String> seenStatuses) {
    }
}

