package com.marmitt.application.spring.reconciliation;

import com.marmitt.application.spring.CTradeApplication;
import com.marmitt.core.domain.runner.Transaction;
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
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Integration test for the T18 reconciliation DB query against real PostgreSQL.
 * Controller HTTP behavior is covered by ReconciliationControllerTest (standaloneSetup).
 */
@Testcontainers
@SpringBootTest(
        classes = CTradeApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ReconciliationIntegrationTest {

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
        registry.add("runner.recovery.transaction.enabled", () -> "false");
    }

    @Autowired
    StrategyRunnerRepositoryPort strategyRunnerRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("TRUNCATE TABLE portfolios CASCADE");
    }

    @Test
    void findFilledBySymbolAndPeriod_returnsFilledTransactionsWithinPeriod() {
        Instant requestedAt = Instant.parse("2026-01-01T10:00:00Z");
        insertFilledTransaction("BTCUSDT", "client-1", new BigDecimal("0.01"), requestedAt);

        List<Transaction> result = strategyRunnerRepository.findFilledBySymbolAndPeriod(
                "BTCUSDT",
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-02T00:00:00Z"));

        assertEquals(1, result.size());
        assertEquals("client-1", result.get(0).getClientOrderId());
        assertEquals(0, new BigDecimal("0.01").compareTo(result.get(0).getExecutedQuantity()));
    }

    @Test
    void findFilledBySymbolAndPeriod_excludesTransactionsOutsidePeriod() {
        Instant outside = Instant.parse("2026-01-03T10:00:00Z"); // after range
        insertFilledTransaction("BTCUSDT", "client-outside", new BigDecimal("0.01"), outside);

        List<Transaction> result = strategyRunnerRepository.findFilledBySymbolAndPeriod(
                "BTCUSDT",
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-02T00:00:00Z"));

        assertEquals(0, result.size());
    }

    @Test
    void findFilledBySymbolAndPeriod_excludesTransactionsByDifferentSymbol() {
        Instant requestedAt = Instant.parse("2026-01-01T10:00:00Z");
        insertFilledTransaction("ETHUSDT", "client-eth", new BigDecimal("1.0"), requestedAt);

        List<Transaction> result = strategyRunnerRepository.findFilledBySymbolAndPeriod(
                "BTCUSDT",
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-02T00:00:00Z"));

        assertEquals(0, result.size());
    }

    @Test
    void findFilledBySymbolAndPeriod_returnsMultipleTransactionsOrderedByRequestedAt() {
        insertFilledTransaction("BTCUSDT", "client-1", new BigDecimal("0.01"), Instant.parse("2026-01-01T08:00:00Z"));
        insertFilledTransaction("BTCUSDT", "client-2", new BigDecimal("0.02"), Instant.parse("2026-01-01T12:00:00Z"));
        insertFilledTransaction("BTCUSDT", "client-3", new BigDecimal("0.03"), Instant.parse("2026-01-01T16:00:00Z"));

        List<Transaction> result = strategyRunnerRepository.findFilledBySymbolAndPeriod(
                "BTCUSDT",
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-02T00:00:00Z"));

        assertEquals(3, result.size());
        assertEquals("client-1", result.get(0).getClientOrderId());
        assertEquals("client-2", result.get(1).getClientOrderId());
        assertEquals("client-3", result.get(2).getClientOrderId());
    }

    private void insertFilledTransaction(String symbol, String clientOrderId, BigDecimal qty, Instant requestedAt) {
        UUID portfolioId = UUID.randomUUID();
        UUID runnerId = UUID.randomUUID();
        String shortCode = UUID.randomUUID().toString().substring(0, 4).toUpperCase();

        jdbcTemplate.update(
                "INSERT INTO portfolios (id, name, is_active, safe_mode_status, capital_pooling_mode, created_at) " +
                "VALUES (?, ?, true, 'NORMAL', 'SHARED', NOW())",
                portfolioId, "test-portfolio-" + portfolioId);

        jdbcTemplate.update(
                "INSERT INTO strategy_runners (id, portfolio_id, short_code, strategy_id, strategy_name, symbol, " +
                "exchange_id, status, status_changed_at, execution_policy, accounting_policy_type, " +
                "max_allocation_percent, max_open_positions, max_pending_orders, is_reconciling, created_at) " +
                "VALUES (?, ?, ?, ?, 'Test Strategy', ?, 'MOCK', 'ACTIVE', NOW(), 'SINGLE', 'FIFO', " +
                "0.1, 1, 1, false, NOW())",
                runnerId, portfolioId, shortCode, UUID.randomUUID(), symbol);

        // executed_at is the time filter column used by findFilledBySymbolAndPeriod (COALESCE(executed_at, requested_at))
        jdbcTemplate.update(
                "INSERT INTO transactions (id, runner_id, client_order_id, exchange_order_id, status, type, " +
                "symbol, quantity, executed_quantity, price, executed_price, total, requested_at, updated_at, executed_at, version) " +
                "VALUES (?, ?, ?, '100234', 'FILLED', 'BUY', ?, ?, ?, 50000, 50000, ?, ?, NOW(), ?, 0)",
                UUID.randomUUID(), runnerId, clientOrderId, symbol,
                qty, qty, qty.multiply(new BigDecimal("50000")),
                Timestamp.from(requestedAt),
                Timestamp.from(requestedAt));
    }
}
