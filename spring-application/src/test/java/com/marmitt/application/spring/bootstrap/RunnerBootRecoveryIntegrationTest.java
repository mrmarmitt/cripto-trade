package com.marmitt.application.spring.bootstrap;

import com.marmitt.application.spring.CTradeApplication;
import com.marmitt.core.application.usecase.runner.RunnerBootRecoveryUseCase;
import com.marmitt.core.domain.portfolio.GlobalBalance;
import com.marmitt.core.domain.runner.ClientOrderId;
import com.marmitt.core.domain.runner.StrategyRunner;
import com.marmitt.core.domain.runner.Transaction;
import com.marmitt.core.dto.portfolio.CreatePortfolioRequest;
import com.marmitt.core.dto.portfolio.CreatePortfolioResponse;
import com.marmitt.core.dto.runner.CreateRunnerRequest;
import com.marmitt.core.dto.runner.CreateRunnerResponse;
import com.marmitt.core.enums.DlqReason;
import com.marmitt.core.enums.TransactionStatus;
import com.marmitt.core.enums.TransactionType;
import com.marmitt.core.ports.inbound.portfolio.CreatePortfolioPort;
import com.marmitt.core.ports.inbound.runner.CreateRunnerPort;
import com.marmitt.core.ports.outbound.repository.DeadLetterEntryRepositoryPort;
import com.marmitt.core.ports.outbound.repository.GlobalBalanceRepositoryPort;
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
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
@SpringBootTest(
        classes = CTradeApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class RunnerBootRecoveryIntegrationTest {

    private static final UUID SMA_STRATEGY_ID =
            UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
    private static final String SYMBOL = "BTCUSDT";
    private static final BigDecimal INITIAL_CAPITAL = new BigDecimal("1000.00000000");
    private static final Duration WAIT_TIMEOUT = Duration.ofSeconds(8);

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
    private RunnerBootRecoveryUseCase runnerBootRecoveryUseCase;

    @Autowired
    private StrategyRunnerRepositoryPort strategyRunnerRepository;

    @Autowired
    private GlobalBalanceRepositoryPort globalBalanceRepository;

    @Autowired
    private DeadLetterEntryRepositoryPort deadLetterEntryRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("TRUNCATE TABLE portfolios CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE capital_event_ledger");
    }

    @Test
    void recoveryShouldExpireZombiePendingBuyAndRestoreBalance() {
        UUID portfolioId = createPortfolio();
        StrategyRunner runner = createAndActivateRunner(portfolioId);
        BigDecimal reservedAmount = new BigDecimal("150.00000000");

        Transaction pendingBuy = newTransaction(
                runner,
                TransactionType.BUY,
                new BigDecimal("0.00300000"),
                new BigDecimal("50000.00000000"),
                reservedAmount
        );
        strategyRunnerRepository.saveTransaction(pendingBuy);
        assertTrue(globalBalanceRepository.reserveAtomic(portfolioId, reservedAmount));

        // Force "zombie" age without depending on TTL property timings.
        jdbcTemplate.update(
                "UPDATE transactions SET requested_at = ? WHERE id = ?",
                Timestamp.from(Instant.now().minus(Duration.ofHours(2))),
                pendingBuy.getId()
        );

        RunnerBootRecoveryUseCase.RecoverySummary summary = runnerBootRecoveryUseCase.recoverRunner(runner);

        Transaction expired = awaitTransactionStatus(pendingBuy.getId(), TransactionStatus.EXPIRED, WAIT_TIMEOUT);
        assertNotNull(expired);
        assertEquals(1, summary.inFlightCount());
        assertEquals(1, summary.zombiesCount());
        assertEquals(0, summary.limboCount());

        awaitBalance(portfolioId, INITIAL_CAPITAL, BigDecimal.ZERO, WAIT_TIMEOUT);

        StrategyRunner latestRunner = strategyRunnerRepository.findById(runner.getId())
                .orElseThrow(() -> new IllegalStateException("Runner not found after recovery"));
        assertEquals(com.marmitt.core.enums.RunnerStatus.ACTIVE, latestRunner.getStatus());
        assertFalse(latestRunner.isReconciling());
    }

    @Test
    void recoveryShouldExpireSubmittedOrderNotFoundOnExchangeAndReleaseBalance() {
        UUID portfolioId = createPortfolio();
        StrategyRunner runner = createAndActivateRunner(portfolioId);
        BigDecimal reservedAmount = new BigDecimal("90.00000000");

        Transaction submittedBuy = newTransaction(
                runner,
                TransactionType.BUY,
                new BigDecimal("0.00150000"),
                new BigDecimal("60000.00000000"),
                reservedAmount
        );
        submittedBuy.submit("EX_MISSING_ON_EXCHANGE");
        strategyRunnerRepository.saveTransaction(submittedBuy);
        assertTrue(globalBalanceRepository.reserveAtomic(portfolioId, reservedAmount));

        RunnerBootRecoveryUseCase.RecoverySummary summary = runnerBootRecoveryUseCase.recoverRunner(runner);

        Transaction expired = awaitTransactionStatus(submittedBuy.getId(), TransactionStatus.EXPIRED, WAIT_TIMEOUT);
        assertNotNull(expired);
        assertEquals(1, summary.inFlightCount());
        assertEquals(0, summary.zombiesCount());
        assertEquals(1, summary.limboCount());

        awaitBalance(portfolioId, INITIAL_CAPITAL, BigDecimal.ZERO, WAIT_TIMEOUT);
    }

    @Test
    void recoveryShouldHaltActiveRunnerWhenUnresolvedDlqExists() {
        UUID portfolioId = createPortfolio();
        StrategyRunner runner = createAndActivateRunner(portfolioId);

        insertUnresolvedDlq(
                portfolioId,
                runner.getId(),
                ClientOrderId.generate(runner.getShortCode(), TransactionType.BUY),
                "EX_DANGLING",
                DlqReason.RETRY_EXHAUSTED
        );
        assertTrue(deadLetterEntryRepository.existsUnresolvedByRunnerId(runner.getId()));

        RunnerBootRecoveryUseCase.RecoverySummary summary = runnerBootRecoveryUseCase.recoverRunner(runner);

        assertEquals(0, summary.inFlightCount());
        assertEquals(0, summary.zombiesCount());
        assertEquals(0, summary.limboCount());

        StrategyRunner latestRunner = strategyRunnerRepository.findById(runner.getId())
                .orElseThrow(() -> new IllegalStateException("Runner not found after recovery"));
        assertEquals(com.marmitt.core.enums.RunnerStatus.HALTED, latestRunner.getStatus());
        assertTrue(latestRunner.isReconciling());
        assertTrue(deadLetterEntryRepository.existsUnresolvedByRunnerId(runner.getId()));
    }

    private UUID createPortfolio() {
        CreatePortfolioResponse response = createPortfolioPort.execute(
                CreatePortfolioRequest.builder()
                        .name("phase1a-boot-" + UUID.randomUUID())
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
        return strategyRunnerRepository.findById(response.runnerId())
                .orElseThrow(() -> new IllegalStateException("Runner not found after activation"));
    }

    private Transaction newTransaction(StrategyRunner runner,
                                       TransactionType type,
                                       BigDecimal quantity,
                                       BigDecimal price,
                                       BigDecimal total) {
        return new Transaction(
                runner.getId(),
                ClientOrderId.generate(runner.getShortCode(), type),
                type,
                SYMBOL,
                quantity,
                price,
                total,
                new BigDecimal("0.80"),
                "phase1a boot recovery test",
                null
        );
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
                + " transactionId=" + transactionId
                + " lastStatus=" + (last == null ? "null" : last.getStatus()));
    }

    private void insertUnresolvedDlq(UUID portfolioId,
                                     UUID runnerId,
                                     String clientOrderId,
                                     String exchangeOrderId,
                                     DlqReason reason) {
        jdbcTemplate.update(
                """
                INSERT INTO dead_letter_entries (
                    id,
                    portfolio_id,
                    runner_id,
                    client_order_id,
                    exchange_order_id,
                    raw_payload,
                    reason,
                    is_resolved,
                    created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, FALSE, ?)
                """,
                UUID.randomUUID(),
                portfolioId,
                runnerId,
                clientOrderId,
                exchangeOrderId,
                "source=test unresolved dlq",
                reason.name(),
                Timestamp.from(Instant.now())
        );
    }

    private GlobalBalance awaitBalance(UUID portfolioId,
                                       BigDecimal expectedAvailable,
                                       BigDecimal expectedReserved,
                                       Duration timeout) {
        Instant deadline = Instant.now().plus(timeout);
        GlobalBalance last = null;
        while (Instant.now().isBefore(deadline)) {
            last = globalBalanceRepository.findByPortfolioId(portfolioId).orElse(null);
            if (last != null
                    && last.getAvailableBalance().compareTo(expectedAvailable) == 0
                    && last.getReservedBalance().compareTo(expectedReserved) == 0) {
                return last;
            }
            sleep(80);
        }
        throw new AssertionError("Timeout waiting global balance"
                + " portfolioId=" + portfolioId
                + " expectedAvailable=" + expectedAvailable
                + " expectedReserved=" + expectedReserved
                + " lastAvailable=" + (last == null ? "null" : last.getAvailableBalance())
                + " lastReserved=" + (last == null ? "null" : last.getReservedBalance()));
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting async processing", e);
        }
    }
}
