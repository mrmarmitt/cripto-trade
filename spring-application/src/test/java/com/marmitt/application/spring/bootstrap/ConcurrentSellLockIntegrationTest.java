package com.marmitt.application.spring.bootstrap;

import com.marmitt.application.spring.CTradeApplication;
import com.marmitt.core.domain.runner.ClientOrderId;
import com.marmitt.core.domain.runner.Position;
import com.marmitt.core.domain.runner.StrategyRunner;
import com.marmitt.core.domain.runner.Transaction;
import com.marmitt.core.dto.portfolio.CreatePortfolioRequest;
import com.marmitt.core.dto.portfolio.CreatePortfolioResponse;
import com.marmitt.core.dto.runner.CreateRunnerRequest;
import com.marmitt.core.dto.runner.CreateRunnerResponse;
import com.marmitt.core.enums.PositionStatus;
import com.marmitt.core.enums.TransactionType;
import com.marmitt.core.ports.inbound.portfolio.CreatePortfolioPort;
import com.marmitt.core.ports.inbound.runner.CreateRunnerPort;
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
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
@SpringBootTest(
        classes = CTradeApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ConcurrentSellLockIntegrationTest {

    private static final UUID SMA_STRATEGY_ID =
            UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
    private static final String SYMBOL = "BTCUSDT";
    private static final String MOCK_EXCHANGE = "MOCK";
    private static final String MARKET_DATA_EXCHANGE = "BINANCE";
    private static final BigDecimal INITIAL_CAPITAL = new BigDecimal("10000.00");
    private static final BigDecimal POSITION_QUANTITY = new BigDecimal("0.00200000");
    private static final BigDecimal POSITION_PRICE = new BigDecimal("65000.00000000");
    private static final Duration LOCK_ATTEMPT_TIMEOUT = Duration.ofSeconds(5);

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
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("TRUNCATE TABLE portfolios CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE capital_event_ledger");
    }

    @Test
    void concurrentSellLocksShouldAllowOnlyOneWinnerForSamePosition() throws Exception {
        UUID portfolioId = createPortfolio();
        StrategyRunner runner = createAndActivateRunner(portfolioId);

        Transaction buy = newBuyTransaction(runner);
        strategyRunnerRepository.saveTransaction(buy);

        Position position = new Position(runner.getId(), SYMBOL, POSITION_QUANTITY, POSITION_PRICE);
        position.associateBuyTransaction(buy.getId());
        strategyRunnerRepository.savePosition(position);

        Transaction firstSell = newSellTransaction(runner, position.getId());
        Transaction secondSell = newSellTransaction(runner, position.getId());
        strategyRunnerRepository.saveTransaction(firstSell);
        strategyRunnerRepository.saveTransaction(secondSell);

        LockResult firstResult;
        LockResult secondResult;
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch startSignal = new CountDownLatch(1);
            Future<LockResult> firstAttempt = executor.submit(() -> tryLockWhenReleased(
                    startSignal,
                    position.getId(),
                    firstSell.getId()
            ));
            Future<LockResult> secondAttempt = executor.submit(() -> tryLockWhenReleased(
                    startSignal,
                    position.getId(),
                    secondSell.getId()
            ));

            startSignal.countDown();

            firstResult = firstAttempt.get(LOCK_ATTEMPT_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            secondResult = secondAttempt.get(LOCK_ATTEMPT_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        long winners = List.of(firstResult, secondResult).stream()
                .filter(LockResult::locked)
                .count();
        assertEquals(1, winners, "Only one concurrent SELL lock can win for the same position");

        LockResult winner = firstResult.locked() ? firstResult : secondResult;
        Position lockedPosition = strategyRunnerRepository.findPositionById(position.getId())
                .orElseThrow(() -> new AssertionError("Position not found after lock: " + position.getId()));

        assertEquals(PositionStatus.CLOSING, lockedPosition.getStatus());
        assertEquals(winner.transactionId(), lockedPosition.getLockedByTransactionId());
        assertEquals(0, lockedPosition.getLockedQuantity().compareTo(POSITION_QUANTITY));
        assertNotNull(lockedPosition.getLockedAt());
        assertEquals(1, countLockedPositions(position.getId()));
    }

    private LockResult tryLockWhenReleased(CountDownLatch startSignal,
                                           UUID positionId,
                                           UUID transactionId) throws InterruptedException {
        assertTrue(startSignal.await(LOCK_ATTEMPT_TIMEOUT.toSeconds(), TimeUnit.SECONDS),
                "Timed out waiting concurrent lock start signal");
        boolean locked = strategyRunnerRepository.tryLockPositionForSell(
                positionId,
                transactionId,
                POSITION_QUANTITY
        );
        return new LockResult(transactionId, locked);
    }

    private UUID createPortfolio() {
        CreatePortfolioResponse response = createPortfolioPort.execute(
                CreatePortfolioRequest.builder()
                        .name("phase2-lock-" + UUID.randomUUID())
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
                        .allowedMarketDataSources(Set.of(MARKET_DATA_EXCHANGE))
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

    private Transaction newBuyTransaction(StrategyRunner runner) {
        return new Transaction(
                runner.getId(),
                ClientOrderId.generate(runner.getShortCode(), TransactionType.BUY),
                TransactionType.BUY,
                SYMBOL,
                POSITION_QUANTITY,
                POSITION_PRICE,
                POSITION_QUANTITY.multiply(POSITION_PRICE),
                new BigDecimal("0.90"),
                "phase2 seed buy position",
                null
        );
    }

    private Transaction newSellTransaction(StrategyRunner runner, UUID targetLotId) {
        return new Transaction(
                runner.getId(),
                ClientOrderId.generate(runner.getShortCode(), TransactionType.SELL),
                TransactionType.SELL,
                SYMBOL,
                POSITION_QUANTITY,
                POSITION_PRICE,
                POSITION_QUANTITY.multiply(POSITION_PRICE),
                new BigDecimal("0.90"),
                "phase2 concurrent sell lock",
                targetLotId
        );
    }

    private int countLockedPositions(UUID positionId) {
        Integer count = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                          FROM positions
                         WHERE id = ?
                           AND status = 'CLOSING'
                           AND locked_by_transaction_id IS NOT NULL
                        """,
                Integer.class,
                positionId
        );
        return count == null ? 0 : count;
    }

    private record LockResult(UUID transactionId, boolean locked) {
    }
}
