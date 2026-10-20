package com.marmitt.application.spring.bootstrap;

import com.marmitt.application.spring.config.exchange.MockExchangeAdapter;
import com.marmitt.application.spring.testsupport.DatabaseCleaner;
import com.marmitt.core.dto.boot.BootExecutionCommand;
import com.marmitt.core.dto.portfolio.request.CreatePortfolioRequest;
import com.marmitt.core.dto.runner.request.CreateRunnerRequest;
import com.marmitt.core.enums.BootAccountQueryPolicy;
import com.marmitt.core.enums.BootFailureMode;
import com.marmitt.core.enums.SafeModeOrigin;
import com.marmitt.core.enums.SafeModeStatus;
import com.marmitt.core.ports.inbound.boot.RunBootSequencePort;
import com.marmitt.core.ports.inbound.portfolio.CreatePortfolioPort;
import com.marmitt.core.ports.inbound.portfolio.EvaluateExchangeBalancePort;
import com.marmitt.core.ports.inbound.portfolio.ManageSafeModePort;
import com.marmitt.core.ports.inbound.runner.CreateRunnerPort;
import com.marmitt.core.ports.outbound.boot.BootExecutionObserverPort;
import com.marmitt.core.ports.outbound.repository.ExchangeAdapterRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * T57 — conferencia de saldo por exchange contra o banco real e o MOCK (10000 USDT): dois portfolios que
 * somam mais do que a conta vao para HALT automatico juntos, no boot e na avaliacao periodica (esta, so na
 * segunda leitura seguida); somando menos, nada acontece.
 */
class ExchangeBalanceReconciliationIntegrationTest extends AbstractIntegrationTest {

    private static final UUID SMA_STRATEGY_ID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");

    @Autowired
    private CreatePortfolioPort createPortfolio;

    @Autowired
    private CreateRunnerPort createRunner;

    @Autowired
    private RunBootSequencePort runBootSequence;

    @Autowired
    private EvaluateExchangeBalancePort evaluateExchangeBalance;

    @Autowired
    private ManageSafeModePort manageSafeMode;

    @Autowired
    private ExchangeAdapterRepositoryPort exchangeAdapterRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        mockExchange().reset();
        DatabaseCleaner.reset(jdbcTemplate);
    }

    @Test
    void bootHaltsEveryPortfolioOfTheExchangeWhenTheirSumExceedsTheAccount() {
        UUID first = portfolioWithMockRunner("6000");
        UUID second = portfolioWithMockRunner("6000");

        runBootSequence.execute(sanityOnlyBoot(), new NoOpObserver());

        assertHaltedAutomatically(first);
        assertHaltedAutomatically(second);
    }

    @Test
    void periodicCheckHaltsOnlyOnTheSecondReadingInDeficit() {
        UUID first = portfolioWithMockRunner("6000");
        UUID second = portfolioWithMockRunner("6000");

        evaluateExchangeBalance.evaluate();
        assertEquals(SafeModeStatus.NORMAL, manageSafeMode.status(first).level(), "primeira leitura so registra");

        evaluateExchangeBalance.evaluate();
        assertHaltedAutomatically(first);
        assertHaltedAutomatically(second);
    }

    @Test
    void portfoliosBelowTheAccountStayNormal() {
        UUID first = portfolioWithMockRunner("4000");
        UUID second = portfolioWithMockRunner("4000");

        runBootSequence.execute(sanityOnlyBoot(), new NoOpObserver());
        evaluateExchangeBalance.evaluate();
        evaluateExchangeBalance.evaluate();

        assertEquals(SafeModeStatus.NORMAL, manageSafeMode.status(first).level());
        assertEquals(SafeModeStatus.NORMAL, manageSafeMode.status(second).level());
    }

    private void assertHaltedAutomatically(UUID portfolioId) {
        assertEquals(SafeModeStatus.HALT, manageSafeMode.status(portfolioId).level());
        assertEquals(SafeModeOrigin.AUTOMATIC, manageSafeMode.status(portfolioId).origin());
    }

    private UUID portfolioWithMockRunner(String capital) {
        UUID portfolioId = createPortfolio.execute(CreatePortfolioRequest.builder()
                .name("t57-" + UUID.randomUUID())
                .initialCapitalAmount(new BigDecimal(capital))
                .currency("USDT")
                .build()).portfolioId();
        assertNotNull(portfolioId);
        assertNotNull(createRunner.execute(CreateRunnerRequest.builder()
                .portfolioId(portfolioId)
                .strategyId(SMA_STRATEGY_ID)
                .symbol("BTCUSDT")
                .exchangeName("MOCK")
                .allowedMarketDataSources(Set.of("BINANCE"))
                .build()).runnerId());
        return portfolioId;
    }

    private static BootExecutionCommand sanityOnlyBoot() {
        return new BootExecutionCommand(false, true, false, true, false, false,
                BootFailureMode.WARN_ONLY, BootAccountQueryPolicy.SKIP);
    }

    private MockExchangeAdapter mockExchange() {
        return exchangeAdapterRepository.findAdapter("MOCK")
                .map(d -> d.streaming())
                .filter(MockExchangeAdapter.class::isInstance)
                .map(MockExchangeAdapter.class::cast)
                .orElseThrow(() -> new IllegalStateException("MOCK adapter not found or has invalid type"));
    }

    private static final class NoOpObserver implements BootExecutionObserverPort {
        @Override public void onRunStarted(String runId, String mode) { }
        @Override public void onPhaseSkipped(String phase) { }
        @Override public void onPhaseStarted(String phase) { }
        @Override public void onPhaseSucceeded(String phase, long durationMs) { }
        @Override public void onPhaseFailed(String phase, long durationMs, String message) { }
        @Override public void onPortfolioPhaseEvaluated(String phase, String status, String exchange, String mode,
                                                        long durationMs) { }
        @Override public void onFailFastRequested(String runId, String phase, String code, String message) { }
        @Override public void onRunCompleted(String runId) { }
        @Override public void onRunFailed(String runId, String failurePhase, String failureMessage) { }
    }
}
