package com.marmitt.application.spring.bootstrap;

import com.marmitt.application.spring.config.exchange.MockExchangeAdapter;
import com.marmitt.core.domain.Symbol;
import com.marmitt.core.domain.runner.StrategyRunner;
import com.marmitt.core.dto.portfolio.request.CreatePortfolioRequest;
import com.marmitt.core.dto.portfolio.response.CreatePortfolioResponse;
import com.marmitt.core.dto.runner.request.CreateRunnerRequest;
import com.marmitt.core.dto.runner.response.CreateRunnerResponse;
import com.marmitt.core.dto.strategy.StrategyContextDto;
import com.marmitt.core.dto.strategy.StrategyInputDto;
import com.marmitt.core.dto.strategy.StrategyOutputDto;
import com.marmitt.core.dto.websocket.data.MarketDataDto;
import com.marmitt.core.ports.inbound.portfolio.CreatePortfolioPort;
import com.marmitt.core.ports.inbound.runner.CreateRunnerPort;
import com.marmitt.core.ports.inbound.runner.ProcessTradeSignalPort;
import com.marmitt.core.ports.outbound.repository.ExchangeAdapterRepositoryPort;
import com.marmitt.core.ports.outbound.repository.StrategyRepositoryPort;
import com.marmitt.core.ports.outbound.repository.StrategyRunnerRepositoryPort;
import com.marmitt.core.ports.outbound.strategy.TradingStrategy;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.search.MeterNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

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

class ProcessTradeSignalMockIntegrationTest extends AbstractIntegrationTest {

    private static final UUID SMA_STRATEGY_ID =
            UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
    private static final String SYMBOL = "BTCUSDT";
    private static final String MARKET_DATA_EXCHANGE = "BINANCE";
    private static final Duration WAIT_TIMEOUT = Duration.ofSeconds(20);

    @Autowired
    private CreatePortfolioPort createPortfolioPort;

    @Autowired
    private CreateRunnerPort createRunnerPort;

    @Autowired
    private ProcessTradeSignalPort processTradeSignalPort;

    @Autowired
    private StrategyRunnerRepositoryPort strategyRunnerRepository;

    @Autowired
    private StrategyRepositoryPort strategyRepository;

    @Autowired
    private ExchangeAdapterRepositoryPort exchangeAdapterRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MeterRegistry meterRegistry;

    @BeforeEach
    void cleanDatabase() {
        MockExchangeAdapter mock = (MockExchangeAdapter) exchangeAdapterRepository
                .findAdapter("MOCK")
                .map(d -> d.streaming())
                .filter(MockExchangeAdapter.class::isInstance)
                .map(MockExchangeAdapter.class::cast)
                .orElseThrow(() -> new IllegalStateException("MOCK adapter not registered"));
        mock.reset();
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

        // T23 G2: a BUY que resultou em ordem despachada deve incrementar signal.evaluated.total{decision=BUY}.
        assertTrue(signalEvaluatedCount(runnerId, "BUY") >= 1.0,
                "Expected signal.evaluated.total{decision=BUY} >= 1 for runner " + runnerId);
    }

    private double signalEvaluatedCount(UUID runnerId, String decision) {
        try {
            return meterRegistry.get("signal.evaluated.total")
                    .tags("runnerId", runnerId.toString(), "decision", decision)
                    .counter()
                    .count();
        } catch (MeterNotFoundException e) {
            return 0.0;
        }
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

    @Test
    void buyAtLimitPricePersistsTransactionAtStrategyPrice() {
        UUID portfolioId = createPortfolio();
        UUID strategyId = UUID.randomUUID();
        BigDecimal limitPrice = new BigDecimal("64000.00"); // abaixo do mercado do tick (65000)
        strategyRepository.registerStrategy(
                new FixedBuyStrategy(strategyId, new BigDecimal("0.01"), limitPrice));
        UUID runnerId = createAndActivateRunner(portfolioId, strategyId);

        processTradeSignalPort.execute(newMarketData(new BigDecimal("65000.00"), Instant.now()));

        BigDecimal persisted = awaitBuyTransactionPrice(runnerId, WAIT_TIMEOUT);
        assertEquals(0, limitPrice.compareTo(persisted),
                "BUY deve persistir com o limitPrice da estrategia (" + limitPrice
                        + "), nao o preco de mercado do tick");
    }

    @Test
    void buyWithoutLimitPricePersistsTransactionAtMarketPrice() {
        UUID portfolioId = createPortfolio();
        UUID strategyId = UUID.randomUUID();
        BigDecimal marketPrice = new BigDecimal("65000.00");
        strategyRepository.registerStrategy(
                new FixedBuyStrategy(strategyId, new BigDecimal("0.01"), null)); // sem limitPrice
        UUID runnerId = createAndActivateRunner(portfolioId, strategyId);

        processTradeSignalPort.execute(newMarketData(marketPrice, Instant.now()));

        BigDecimal persisted = awaitBuyTransactionPrice(runnerId, WAIT_TIMEOUT);
        assertEquals(0, marketPrice.compareTo(persisted),
                "Sem limitPrice, a BUY deve persistir com o preco de mercado do tick (" + marketPrice + ")");
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
        return createAndActivateRunner(portfolioId, SMA_STRATEGY_ID);
    }

    private UUID createAndActivateRunner(UUID portfolioId, UUID strategyId) {
        CreateRunnerResponse response = createRunnerPort.execute(
                CreateRunnerRequest.builder()
                        .portfolioId(portfolioId)
                        .strategyId(strategyId)
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

    private BigDecimal awaitBuyTransactionPrice(UUID runnerId, Duration timeout) {
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            List<BigDecimal> prices = jdbcTemplate.query(
                    "SELECT price FROM transactions WHERE runner_id = ? AND type = 'BUY' "
                            + "ORDER BY requested_at DESC LIMIT 1",
                    (rs, rowNum) -> rs.getBigDecimal("price"),
                    runnerId
            );
            if (!prices.isEmpty()) {
                return prices.getFirst();
            }
            sleep(60);
        }
        fail("Timeout waiting BUY transaction to be persisted for runner " + runnerId);
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

    /**
     * Estrategia de teste deterministica: abre uma unica BUY (com ou sem limitPrice) enquanto nao
     * houver posicao/ordem aberta; depois segura. Usada para provar o fluxo do limitPrice (T34) sem
     * depender das estrategias de cenario da T35.
     */
    private static final class FixedBuyStrategy implements TradingStrategy {
        private final UUID id;
        private final BigDecimal quantity;
        private final BigDecimal limitPrice; // null = usar preco de mercado
        private boolean enabled = true;

        FixedBuyStrategy(UUID id, BigDecimal quantity, BigDecimal limitPrice) {
            this.id = id;
            this.quantity = quantity;
            this.limitPrice = limitPrice;
        }

        @Override
        public UUID getStrategyId() {
            return id;
        }

        @Override
        public StrategyOutputDto executeStrategy(StrategyInputDto inputData, StrategyContextDto strategyContext) {
            if (strategyContext.hasOpenLots() || strategyContext.hasPendingOrders()) {
                return StrategyOutputDto.hold(getStrategyName(), "posicao/ordem ja aberta");
            }
            BigDecimal confidence = new BigDecimal("0.8");
            if (limitPrice != null) {
                return StrategyOutputDto.buyAt(getStrategyName(), confidence, quantity, limitPrice, "fixed limit buy");
            }
            return StrategyOutputDto.buy(getStrategyName(), confidence, quantity, "fixed market buy");
        }

        @Override
        public String getStrategyName() {
            return "FixedBuyStrategy-" + id;
        }

        @Override
        public String getStrategyVersion() {
            return "1.0.0";
        }

        @Override
        public boolean isEnabled() {
            return enabled;
        }

        @Override
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}


