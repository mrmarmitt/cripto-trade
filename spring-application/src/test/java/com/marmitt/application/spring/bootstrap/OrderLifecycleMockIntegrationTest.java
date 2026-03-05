package com.marmitt.application.spring.bootstrap;

import com.marmitt.application.spring.CTradeApplication;
import com.marmitt.core.domain.Symbol;
import com.marmitt.core.domain.runner.StrategyRunner;
import com.marmitt.core.dto.portfolio.CreatePortfolioRequest;
import com.marmitt.core.dto.portfolio.CreatePortfolioResponse;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@Testcontainers
@SpringBootTest(
        classes = CTradeApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class OrderLifecycleMockIntegrationTest {

    private static final UUID SMA_STRATEGY_ID =
            UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
    private static final String SYMBOL = "BTCUSDT";
    private static final String MARKET_DATA_EXCHANGE = "BINANCE";
    private static final Duration WAIT_TIMEOUT = Duration.ofSeconds(25);
    // Sentinel de regressao: no estado atual, SELL FILLED pode deixar pequeno residual
    // de reserva por diferenca entre custo reservado e custo efetivo consolidado.
    private static final BigDecimal MAX_ACCEPTABLE_RESERVED_RESIDUAL = new BigDecimal("2.00000000");

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
    void buyThenSellShouldPersistMatchesClosePositionAndRealizeBalance() {
        UUID portfolioId = createPortfolio();
        UUID runnerId = createAndActivateRunner(portfolioId);

        publishBuyTriggerTicks();
        TransactionLifecycle buyLifecycle = awaitTransactionLifecycle(runnerId, "BUY", WAIT_TIMEOUT);
        assertEquals("FILLED", buyLifecycle.finalTransaction().status());
        assertTrue(buyLifecycle.seenStatuses().contains("PARTIAL"),
                "BUY should pass through PARTIAL before FILLED. Seen=" + buyLifecycle.seenStatuses());

        PositionSnapshot openBeforeSell = latestOpenPosition(runnerId, SYMBOL);
        assertNotNull(openBeforeSell, "Expected OPEN position after BUY fill");

        publishSellTriggerTick();
        TransactionLifecycle sellLifecycle = awaitTransactionLifecycle(runnerId, "SELL", WAIT_TIMEOUT);
        TransactionSnapshot sell = sellLifecycle.finalTransaction();
        assertEquals("FILLED", sell.status());
        assertTrue(sellLifecycle.seenStatuses().contains("SUBMITTED"),
                "SELL lifecycle should include SUBMITTED before FILLED. Seen=" + sellLifecycle.seenStatuses());

        MatchAggregateSnapshot matchAggregate = awaitSellMatchAggregation(sell.id(), WAIT_TIMEOUT);
        assertTrue(matchAggregate.matchCount() >= 1, "Expected at least one transaction_match for SELL fill");
        assertEquals(0, sell.executedQuantity().compareTo(matchAggregate.totalMatchedQuantity()),
                "Sum of matched_quantity must equal SELL executed_quantity");

        PositionSnapshot openAfterSell = latestOpenPosition(runnerId, SYMBOL);
        assertNull(openAfterSell, "Expected no OPEN position after SELL filled");

        PositionSnapshot lastClosed = awaitClosedPosition(runnerId, SYMBOL, WAIT_TIMEOUT);
        assertNotNull(lastClosed, "Expected CLOSED position after SELL filled");

        GlobalBalanceSnapshot balance = awaitPostSellBalance(portfolioId, WAIT_TIMEOUT);
        assertTrue(balance.reservedBalance().compareTo(BigDecimal.ZERO) >= 0,
                "Reserved balance must never be negative");
        assertTrue(balance.reservedBalance().compareTo(MAX_ACCEPTABLE_RESERVED_RESIDUAL) < 0,
                "Reserved balance residual should stay small in this scenario");
        assertTrue(balance.realizedBalance().compareTo(BigDecimal.ZERO) > 0,
                "Expected positive realized balance in this scenario");
    }

    private UUID createPortfolio() {
        CreatePortfolioResponse response = createPortfolioPort.execute(
                CreatePortfolioRequest.builder()
                        .name("phase1b-" + UUID.randomUUID())
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

    private void publishSellTriggerTick() {
        processTradeSignalPort.execute(newMarketData(new BigDecimal("80000.00"), Instant.now().plusSeconds(1)));
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

    private TransactionLifecycle awaitTransactionLifecycle(UUID runnerId,
                                                           String type,
                                                           Duration timeout) {
        Instant deadline = Instant.now().plus(timeout);
        LinkedHashSet<String> seenStatuses = new LinkedHashSet<>();
        TransactionSnapshot lastSeen = null;

        while (Instant.now().isBefore(deadline)) {
            TransactionSnapshot tx = latestTransactionByType(runnerId, type);
            if (tx != null) {
                lastSeen = tx;
                seenStatuses.add(tx.status());
                if ("FILLED".equals(tx.status())) {
                    return new TransactionLifecycle(tx, Set.copyOf(seenStatuses));
                }
                if ("REJECTED".equals(tx.status()) || "CANCELED".equals(tx.status()) || "EXPIRED".equals(tx.status())) {
                    fail(type + " should end as FILLED but finished as " + tx.status() + " tx=" + tx);
                }
            }
            sleep(80);
        }

        throw new AssertionError("Timeout waiting " + type + " lifecycle. Last tx=" + lastSeen
                + ", seenStatuses=" + seenStatuses);
    }

    private MatchAggregateSnapshot awaitSellMatchAggregation(UUID sellTransactionId, Duration timeout) {
        Instant deadline = Instant.now().plus(timeout);
        MatchAggregateSnapshot lastSeen = null;
        while (Instant.now().isBefore(deadline)) {
            MatchAggregateSnapshot snapshot = matchAggregateBySellTransaction(sellTransactionId);
            if (snapshot.matchCount() > 0) {
                return snapshot;
            }
            lastSeen = snapshot;
            sleep(80);
        }
        throw new AssertionError("Timeout waiting transaction_matches for sellTransactionId="
                + sellTransactionId + ". Last aggregate=" + lastSeen);
    }

    private GlobalBalanceSnapshot awaitPostSellBalance(UUID portfolioId, Duration timeout) {
        Instant deadline = Instant.now().plus(timeout);
        GlobalBalanceSnapshot lastSeen = null;
        while (Instant.now().isBefore(deadline)) {
            GlobalBalanceSnapshot snapshot = globalBalanceSnapshot(portfolioId);
            if (snapshot != null && snapshot.realizedBalance().compareTo(BigDecimal.ZERO) != 0) {
                return snapshot;
            }
            lastSeen = snapshot;
            sleep(80);
        }
        throw new AssertionError("Timeout waiting post-sell global_balance for portfolio="
                + portfolioId + ". Last snapshot=" + lastSeen);
    }

    private PositionSnapshot awaitClosedPosition(UUID runnerId, String symbol, Duration timeout) {
        Instant deadline = Instant.now().plus(timeout);
        PositionSnapshot lastSeen = null;
        while (Instant.now().isBefore(deadline)) {
            PositionSnapshot closed = latestClosedPosition(runnerId, symbol);
            if (closed != null) {
                return closed;
            }
            lastSeen = closed;
            sleep(80);
        }
        throw new AssertionError("Timeout waiting CLOSED position for runner=" + runnerId
                + " symbol=" + symbol + ". Last snapshot=" + lastSeen);
    }

    private TransactionSnapshot latestTransactionByType(UUID runnerId, String type) {
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
                           AND type = ?
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
                runnerId, type
        );
        return results.isEmpty() ? null : results.getFirst();
    }

    private MatchAggregateSnapshot matchAggregateBySellTransaction(UUID sellTransactionId) {
        List<MatchAggregateSnapshot> results = jdbcTemplate.query(
                """
                        SELECT COUNT(*) AS match_count,
                               COALESCE(SUM(matched_quantity), 0) AS total_matched_quantity
                          FROM transaction_matches
                         WHERE sell_transaction_id = ?
                        """,
                (rs, rowNum) -> new MatchAggregateSnapshot(
                        rs.getInt("match_count"),
                        rs.getBigDecimal("total_matched_quantity")
                ),
                sellTransactionId
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

    private PositionSnapshot latestClosedPosition(UUID runnerId, String symbol) {
        List<PositionSnapshot> results = jdbcTemplate.query(
                """
                        SELECT id, runner_id, symbol, status, quantity, opened_by_transaction_id
                          FROM positions
                         WHERE runner_id = ?
                           AND UPPER(symbol) = UPPER(?)
                           AND status = 'CLOSED'
                         ORDER BY closed_at DESC
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

    private GlobalBalanceSnapshot globalBalanceSnapshot(UUID portfolioId) {
        List<GlobalBalanceSnapshot> results = jdbcTemplate.query(
                """
                        SELECT available_balance, reserved_balance, realized_balance
                          FROM global_balances
                         WHERE portfolio_id = ?
                        """,
                (rs, rowNum) -> new GlobalBalanceSnapshot(
                        rs.getBigDecimal("available_balance"),
                        rs.getBigDecimal("reserved_balance"),
                        rs.getBigDecimal("realized_balance")
                ),
                portfolioId
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

    private record TransactionLifecycle(TransactionSnapshot finalTransaction,
                                        Set<String> seenStatuses) {
    }

    private record MatchAggregateSnapshot(int matchCount,
                                          BigDecimal totalMatchedQuantity) {
    }

    private record PositionSnapshot(UUID id,
                                    UUID runnerId,
                                    String symbol,
                                    String status,
                                    BigDecimal quantity,
                                    UUID openedByTransactionId) {
    }

    private record GlobalBalanceSnapshot(BigDecimal availableBalance,
                                         BigDecimal reservedBalance,
                                         BigDecimal realizedBalance) {
    }
}
