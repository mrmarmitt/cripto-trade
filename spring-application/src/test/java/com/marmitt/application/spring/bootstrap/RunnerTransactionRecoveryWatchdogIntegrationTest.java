package com.marmitt.application.spring.bootstrap;

import com.marmitt.application.spring.CTradeApplication;
import com.marmitt.application.spring.config.exchange.MockExchangeAdapter;
import com.marmitt.core.domain.Symbol;
import com.marmitt.core.domain.portfolio.DeadLetterEntry;
import com.marmitt.core.domain.runner.ClientOrderId;
import com.marmitt.core.domain.runner.StrategyRunner;
import com.marmitt.core.domain.runner.Transaction;
import com.marmitt.core.dto.portfolio.request.CreatePortfolioRequest;
import com.marmitt.core.dto.portfolio.response.CreatePortfolioResponse;
import com.marmitt.core.dto.runner.response.RecoverStaleTransactionsResponse;
import com.marmitt.core.dto.runner.request.CreateRunnerRequest;
import com.marmitt.core.dto.runner.response.CreateRunnerResponse;
import com.marmitt.core.dto.websocket.data.OrderDataDto;
import com.marmitt.core.enums.AccountingPolicyType;
import com.marmitt.core.enums.ExecutionPolicy;
import com.marmitt.core.enums.RunnerStatus;
import com.marmitt.core.enums.TransactionStatus;
import com.marmitt.core.enums.TransactionType;
import com.marmitt.core.exceptions.ExchangeQueryException;
import com.marmitt.core.ports.inbound.portfolio.CreatePortfolioPort;
import com.marmitt.core.ports.inbound.runner.CreateRunnerPort;
import com.marmitt.core.ports.outbound.repository.DeadLetterEntryRepositoryPort;
import com.marmitt.core.ports.outbound.repository.ExchangeAdapterRepositoryPort;
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
import java.util.List;
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
class RunnerTransactionRecoveryWatchdogIntegrationTest {

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
        registry.add("runner.recovery.transaction.interval-ms", () -> 86_400_000L);
        registry.add("runner.recovery.transaction.stale-threshold-ms", () -> 30_000L);
        registry.add("runner.recovery.transaction.max-per-run", () -> 50);
    }

    @Autowired
    private CreatePortfolioPort createPortfolioPort;

    @Autowired
    private CreateRunnerPort createRunnerPort;

    @Autowired
    private RunnerTransactionRecoveryWatchdog watchdog;

    @Autowired
    private RunnerTransactionRecoveryProperties properties;

    @Autowired
    private StrategyRunnerRepositoryPort strategyRunnerRepository;

    @Autowired
    private DeadLetterEntryRepositoryPort deadLetterEntryRepository;

    @Autowired
    private GlobalBalanceRepositoryPort globalBalanceRepository;

    @Autowired
    private ExchangeAdapterRepositoryPort exchangeAdapterRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabaseAndResetProperties() {
        jdbcTemplate.execute("TRUNCATE TABLE portfolios CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE capital_event_ledger");
        properties.setEnabled(true);
        properties.setIntervalMs(86_400_000L);
        properties.setStaleThresholdMs(30_000L);
        properties.setMaxPerRun(50);
    }

    @Test
    void watchdogShouldRecoverStaleSubmittedOrderFoundAsFilled() {
        UUID portfolioId = createPortfolio();
        StrategyRunner runner = createAndActivateRunner(portfolioId);
        BigDecimal quantity = new BigDecimal("0.00150000");
        BigDecimal fillPrice = new BigDecimal("60000.00000000");
        BigDecimal reservedAmount = quantity.multiply(fillPrice);

        Transaction submittedBuy = newTransaction(
                runner,
                TransactionType.BUY,
                quantity,
                fillPrice,
                reservedAmount
        );
        submittedBuy.submit("EX_RUNTIME_FILLED");
        strategyRunnerRepository.saveTransaction(submittedBuy);
        assertTrue(globalBalanceRepository.reserveAtomic(portfolioId, reservedAmount));
        markTransactionStale(submittedBuy.getId(), Duration.ofHours(2));

        getMockExchangeAdapter().seedQueriedOrderSnapshot(orderData(
                submittedBuy,
                OrderDataDto.OrderStatus.FILLED,
                quantity,
                fillPrice,
                BigDecimal.ZERO
        ));

        RecoverStaleTransactionsResponse response = watchdog.runRecoveryCycle();

        Transaction filled = awaitTransactionStatus(submittedBuy.getId(), TransactionStatus.FILLED, WAIT_TIMEOUT);
        assertNotNull(filled);
        assertEquals(1, response.scanned());
        assertEquals(1, response.recovered());
        assertEquals(0, response.routedToDlq());
        assertEquals(0, response.failed());
    }

    @Test
    void watchdogShouldRouteMissingOrderToSingleDlqEntryAcrossRepeatedCycles() {
        UUID portfolioId = createPortfolio();
        StrategyRunner runner = createAndActivateRunner(portfolioId);

        Transaction submittedBuy = newTransaction(
                runner,
                TransactionType.BUY,
                new BigDecimal("0.00200000"),
                new BigDecimal("50000.00000000"),
                new BigDecimal("100.00000000")
        );
        submittedBuy.submit("EX_RUNTIME_MISSING");
        strategyRunnerRepository.saveTransaction(submittedBuy);
        markTransactionStale(submittedBuy.getId(), Duration.ofHours(2));

        RecoverStaleTransactionsResponse first = watchdog.runRecoveryCycle();
        List<DeadLetterEntry> firstEntries = deadLetterEntryRepository.findUnresolved(portfolioId, runner.getId(), 10);

        RecoverStaleTransactionsResponse second = watchdog.runRecoveryCycle();
        List<DeadLetterEntry> secondEntries = deadLetterEntryRepository.findUnresolved(portfolioId, runner.getId(), 10);

        Transaction stillSubmitted = strategyRunnerRepository.findTransactionById(submittedBuy.getId())
                .orElseThrow(() -> new IllegalStateException("Transaction not found after watchdog"));

        assertEquals(TransactionStatus.SUBMITTED, stillSubmitted.getStatus());
        assertEquals(1, first.scanned());
        assertEquals(1, first.routedToDlq());
        assertEquals(1, second.scanned());
        assertEquals(1, second.routedToDlq());
        assertEquals(1, firstEntries.size());
        assertEquals(1, secondEntries.size());
    }

    @Test
    void watchdogShouldContinueAfterTransientQueryFailureAndRecoverNextCandidate() {
        UUID portfolioId = createPortfolio();
        StrategyRunner runner = createAndActivateRunner(portfolioId);

        Transaction first = newSubmittedTransaction(runner, "EX_RUNTIME_TRANSIENT_1");
        Transaction second = newSubmittedTransaction(runner, "EX_RUNTIME_TRANSIENT_2");
        strategyRunnerRepository.saveTransaction(first);
        strategyRunnerRepository.saveTransaction(second);
        markTransactionStale(first.getId(), Duration.ofHours(3));
        markTransactionStale(second.getId(), Duration.ofHours(2));

        MockExchangeAdapter mock = getMockExchangeAdapter();
        mock.seedQueriedOrderSnapshot(orderData(
                first,
                OrderDataDto.OrderStatus.FILLED,
                first.getQuantity(),
                first.getPrice(),
                BigDecimal.ZERO
        ));
        mock.seedQueriedOrderSnapshot(orderData(
                second,
                OrderDataDto.OrderStatus.FILLED,
                second.getQuantity(),
                second.getPrice(),
                BigDecimal.ZERO
        ));
        mock.registerQueryFailurePlan(
                first.getClientOrderId(),
                1,
                ExchangeQueryException.ErrorType.TEMPORARY,
                "simulated temporary timeout"
        );

        RecoverStaleTransactionsResponse firstCycle = watchdog.runRecoveryCycle();

        Transaction firstAfterCycle = strategyRunnerRepository.findTransactionById(first.getId())
                .orElseThrow(() -> new IllegalStateException("First transaction not found"));
        Transaction secondAfterCycle = awaitTransactionStatus(second.getId(), TransactionStatus.FILLED, WAIT_TIMEOUT);

        assertEquals(TransactionStatus.SUBMITTED, firstAfterCycle.getStatus());
        assertEquals(TransactionStatus.FILLED, secondAfterCycle.getStatus());
        assertEquals(2, firstCycle.scanned());
        assertEquals(1, firstCycle.recovered());
        assertEquals(1, firstCycle.failed());

        RecoverStaleTransactionsResponse secondCycle = watchdog.runRecoveryCycle();
        Transaction firstRecovered = awaitTransactionStatus(first.getId(), TransactionStatus.FILLED, WAIT_TIMEOUT);

        assertEquals(TransactionStatus.FILLED, firstRecovered.getStatus());
        assertEquals(1, secondCycle.scanned());
        assertEquals(1, secondCycle.recovered());
        assertEquals(0, secondCycle.failed());
    }

    @Test
    void watchdogShouldRespectMaxPerRun() {
        UUID portfolioId = createPortfolio();
        StrategyRunner runner = createAndActivateRunner(portfolioId);
        properties.setMaxPerRun(1);

        Transaction first = newSubmittedTransaction(runner, "EX_RUNTIME_LIMIT_1");
        Transaction second = newSubmittedTransaction(runner, "EX_RUNTIME_LIMIT_2");
        strategyRunnerRepository.saveTransaction(first);
        strategyRunnerRepository.saveTransaction(second);
        markTransactionStale(first.getId(), Duration.ofHours(3));
        markTransactionStale(second.getId(), Duration.ofHours(2));

        MockExchangeAdapter mock = getMockExchangeAdapter();
        mock.seedQueriedOrderSnapshot(orderData(first, OrderDataDto.OrderStatus.FILLED, first.getQuantity(), first.getPrice(), BigDecimal.ZERO));
        mock.seedQueriedOrderSnapshot(orderData(second, OrderDataDto.OrderStatus.FILLED, second.getQuantity(), second.getPrice(), BigDecimal.ZERO));

        RecoverStaleTransactionsResponse response = watchdog.runRecoveryCycle();

        Transaction firstAfterCycle = awaitTransactionStatus(first.getId(), TransactionStatus.FILLED, WAIT_TIMEOUT);
        Transaction secondAfterCycle = strategyRunnerRepository.findTransactionById(second.getId())
                .orElseThrow(() -> new IllegalStateException("Second transaction not found"));

        assertEquals(1, response.scanned());
        assertEquals(1, response.recovered());
        assertEquals(TransactionStatus.FILLED, firstAfterCycle.getStatus());
        assertEquals(TransactionStatus.SUBMITTED, secondAfterCycle.getStatus());
    }

    @Test
    void watchdogShouldDoNothingWhenDisabled() {
        UUID portfolioId = createPortfolio();
        StrategyRunner runner = createAndActivateRunner(portfolioId);
        properties.setEnabled(false);

        Transaction submitted = newSubmittedTransaction(runner, "EX_RUNTIME_DISABLED");
        strategyRunnerRepository.saveTransaction(submitted);
        markTransactionStale(submitted.getId(), Duration.ofHours(2));

        getMockExchangeAdapter().seedQueriedOrderSnapshot(orderData(
                submitted,
                OrderDataDto.OrderStatus.FILLED,
                submitted.getQuantity(),
                submitted.getPrice(),
                BigDecimal.ZERO
        ));

        RecoverStaleTransactionsResponse response = watchdog.runRecoveryCycle();
        Transaction afterCycle = strategyRunnerRepository.findTransactionById(submitted.getId())
                .orElseThrow(() -> new IllegalStateException("Transaction not found after disabled watchdog"));

        assertEquals(0, response.scanned());
        assertEquals(0, response.recovered());
        assertEquals(TransactionStatus.SUBMITTED, afterCycle.getStatus());
        assertTrue(deadLetterEntryRepository.findUnresolved(portfolioId, runner.getId(), 10).isEmpty());
    }

    private UUID createPortfolio() {
        CreatePortfolioResponse response = createPortfolioPort.execute(
                CreatePortfolioRequest.builder()
                        .name("runtime-recovery-" + UUID.randomUUID())
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

    private Transaction newSubmittedTransaction(StrategyRunner runner, String exchangeOrderId) {
        Transaction tx = newTransaction(
                runner,
                TransactionType.BUY,
                new BigDecimal("0.00100000"),
                new BigDecimal("50000.00000000"),
                new BigDecimal("50.00000000")
        );
        tx.submit(exchangeOrderId);
        return tx;
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
                "runtime recovery watchdog test",
                null
        );
    }

    private void markTransactionStale(UUID transactionId, Duration age) {
        Instant staleAt = Instant.now().minus(age);
        jdbcTemplate.update(
                "UPDATE transactions SET updated_at = ? WHERE id = ?",
                Timestamp.from(staleAt),
                transactionId
        );
    }

    private MockExchangeAdapter getMockExchangeAdapter() {
        return exchangeAdapterRepository.findStreamingByName("MOCK")
                .filter(MockExchangeAdapter.class::isInstance)
                .map(MockExchangeAdapter.class::cast)
                .orElseThrow(() -> new IllegalStateException("MOCK adapter not found or has invalid type"));
    }

    private OrderDataDto orderData(Transaction transaction,
                                   OrderDataDto.OrderStatus status,
                                   BigDecimal executedQuantity,
                                   BigDecimal executedPrice,
                                   BigDecimal fee) {
        return new OrderDataDto(
                transaction.getExchangeOrderId(),
                transaction.getClientOrderId(),
                Symbol.of(SYMBOL),
                transaction.isBuy() ? OrderDataDto.OrderSide.BUY : OrderDataDto.OrderSide.SELL,
                OrderDataDto.OrderType.LIMIT,
                transaction.getQuantity(),
                executedQuantity,
                transaction.getPrice(),
                executedPrice,
                fee,
                status,
                null,
                Instant.now()
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

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting async processing", e);
        }
    }
}
