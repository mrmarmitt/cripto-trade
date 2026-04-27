package com.marmitt.application.spring.bootstrap;

import com.marmitt.application.spring.CTradeApplication;
import com.marmitt.application.spring.handler.CapitalEventListener;
import com.marmitt.core.domain.portfolio.DeadLetterEntry;
import com.marmitt.core.domain.portfolio.GlobalBalance;
import com.marmitt.core.domain.runner.ClientOrderId;
import com.marmitt.core.domain.runner.StrategyRunner;
import com.marmitt.core.domain.shared.Fee;
import com.marmitt.core.dto.capital.ExecutionConfirmation;
import com.marmitt.core.dto.capital.MarginRelease;
import com.marmitt.core.dto.events.ExecutionConfirmedEvent;
import com.marmitt.core.dto.events.MarginReleaseEvent;
import com.marmitt.core.dto.portfolio.request.CreatePortfolioRequest;
import com.marmitt.core.dto.portfolio.response.CreatePortfolioResponse;
import com.marmitt.core.dto.runner.request.CreateRunnerRequest;
import com.marmitt.core.dto.runner.response.CreateRunnerResponse;
import com.marmitt.core.enums.ReleaseReason;
import com.marmitt.core.enums.TransactionType;
import com.marmitt.core.ports.inbound.portfolio.CreatePortfolioPort;
import com.marmitt.core.ports.inbound.runner.CreateRunnerPort;
import com.marmitt.core.ports.outbound.repository.DeadLetterEntryRepositoryPort;
import com.marmitt.core.ports.outbound.repository.GlobalBalanceRepositoryPort;
import com.marmitt.core.ports.outbound.repository.StrategyRunnerRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(
        classes = CTradeApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class CapitalDeadLetterReplayIntegrationTest {

    private static final UUID SMA_STRATEGY_ID =
            UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
    private static final BigDecimal INITIAL_CAPITAL = new BigDecimal("1000.00000000");
    private static final BigDecimal RESERVED_AMOUNT = new BigDecimal("650.00000000");
    private static final BigDecimal REALIZED_PNL = new BigDecimal("15.00000000");
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
    private StrategyRunnerRepositoryPort strategyRunnerRepository;

    @Autowired
    private GlobalBalanceRepositoryPort globalBalanceRepository;

    @Autowired
    private DeadLetterEntryRepositoryPort deadLetterEntryRepository;

    @Autowired
    private CapitalEventListener capitalEventListener;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("TRUNCATE TABLE portfolios CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE capital_event_ledger");
    }

    @Test
    void executionConfirmedDlqReplayShouldApplyEffectOnceAndResolveDuplicateReplaySafely() throws Exception {
        UUID portfolioId = createPortfolio();
        StrategyRunner runner = createAndActivateRunner(portfolioId);
        assertTrue(globalBalanceRepository.reserveAtomic(portfolioId, RESERVED_AMOUNT));

        UUID transactionId = UUID.randomUUID();
        UUID matchId = UUID.randomUUID();
        ExecutionConfirmedEvent event = executionConfirmedEvent(transactionId, runner.getId(), matchId);

        capitalEventListener.recoverExecutionConfirmed(new RuntimeException("planned execution failure"), event);

        DeadLetterEntry firstEntry = awaitUnresolvedEntry(
                portfolioId,
                runner.getId(),
                "\"eventType\":\"EXECUTION_CONFIRMED\"",
                transactionId.toString(),
                WAIT_TIMEOUT
        );

        assertBalance(portfolioId, new BigDecimal("350.00000000"), RESERVED_AMOUNT, BigDecimal.ZERO);

        mockMvc.perform(post("/api/dead-letters/{id}/reprocess", firstEntry.getId())
                        .contentType("application/json")
                        .content("""
                                {
                                  "requestedBy": "operator@test",
                                  "resolutionNote": "replay execution confirmed"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reprocessed").value(true))
                .andExpect(jsonPath("$.entry.resolvedBy").value("operator@test"));

        assertBalance(portfolioId, new BigDecimal("1015.00000000"), BigDecimal.ZERO, REALIZED_PNL);
        assertResolved(firstEntry.getId(), "operator@test");
        assertCapitalLedgerCount("EXECUTION_CONFIRMED", matchId, 1);

        capitalEventListener.recoverExecutionConfirmed(new RuntimeException("planned execution failure duplicate"), event);

        DeadLetterEntry duplicateEntry = awaitUnresolvedEntry(
                portfolioId,
                runner.getId(),
                "\"eventType\":\"EXECUTION_CONFIRMED\"",
                transactionId.toString(),
                WAIT_TIMEOUT
        );

        mockMvc.perform(post("/api/dead-letters/{id}/reprocess", duplicateEntry.getId())
                        .contentType("application/json")
                        .content("""
                                {
                                  "requestedBy": "operator@test",
                                  "resolutionNote": "replay execution confirmed duplicate"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reprocessed").value(true))
                .andExpect(jsonPath("$.entry.resolvedBy").value("operator@test"));

        assertBalance(portfolioId, new BigDecimal("1015.00000000"), BigDecimal.ZERO, REALIZED_PNL);
        assertResolved(duplicateEntry.getId(), "operator@test");
        assertCapitalLedgerCount("EXECUTION_CONFIRMED", matchId, 1);
    }

    @Test
    void marginReleaseDlqReplayShouldApplyEffectAndKeepDuplicateReplayOpenWithoutDrift() throws Exception {
        UUID portfolioId = createPortfolio();
        StrategyRunner runner = createAndActivateRunner(portfolioId);
        assertTrue(globalBalanceRepository.reserveAtomic(portfolioId, RESERVED_AMOUNT));

        UUID transactionId = UUID.randomUUID();
        MarginReleaseEvent event = marginReleaseEvent(transactionId, runner.getId());

        capitalEventListener.recoverMarginRelease(new RuntimeException("planned margin failure"), event);

        DeadLetterEntry firstEntry = awaitUnresolvedEntry(
                portfolioId,
                runner.getId(),
                "\"eventType\":\"MARGIN_RELEASE\"",
                transactionId.toString(),
                WAIT_TIMEOUT
        );

        assertBalance(portfolioId, new BigDecimal("350.00000000"), RESERVED_AMOUNT, BigDecimal.ZERO);

        mockMvc.perform(post("/api/dead-letters/{id}/reprocess", firstEntry.getId())
                        .contentType("application/json")
                        .content("""
                                {
                                  "requestedBy": "operator@test",
                                  "resolutionNote": "replay margin release"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reprocessed").value(true))
                .andExpect(jsonPath("$.entry.resolvedBy").value("operator@test"));

        assertBalance(portfolioId, INITIAL_CAPITAL, BigDecimal.ZERO, BigDecimal.ZERO);
        assertResolved(firstEntry.getId(), "operator@test");
        assertCapitalLedgerCount("MARGIN_RELEASE", transactionId, 1);

        capitalEventListener.recoverMarginRelease(new RuntimeException("planned margin failure duplicate"), event);

        DeadLetterEntry duplicateEntry = awaitUnresolvedEntry(
                portfolioId,
                runner.getId(),
                "\"eventType\":\"MARGIN_RELEASE\"",
                transactionId.toString(),
                WAIT_TIMEOUT
        );

        mockMvc.perform(post("/api/dead-letters/{id}/reprocess", duplicateEntry.getId())
                        .contentType("application/json")
                        .content("""
                                {
                                  "requestedBy": "operator@test",
                                  "resolutionNote": "replay margin release duplicate"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.reprocessed").value(false))
                .andExpect(jsonPath("$.message").value(
                        "Dead letter replay produced no state change and requires manual review"
                ));

        assertBalance(portfolioId, INITIAL_CAPITAL, BigDecimal.ZERO, BigDecimal.ZERO);
        assertFalse(deadLetterEntryRepository.findById(duplicateEntry.getId())
                .orElseThrow(() -> new IllegalStateException("Dead letter not found after duplicate replay"))
                .isResolved());
        assertCapitalLedgerCount("MARGIN_RELEASE", transactionId, 1);
    }

    private UUID createPortfolio() {
        CreatePortfolioResponse response = createPortfolioPort.execute(
                CreatePortfolioRequest.builder()
                        .name("phase4-capital-dlq-" + UUID.randomUUID())
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
                        .symbol("BTCUSDT")
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

    private ExecutionConfirmedEvent executionConfirmedEvent(UUID transactionId, UUID runnerId, UUID matchId) {
        return new ExecutionConfirmedEvent(new ExecutionConfirmation(
                transactionId,
                runnerId,
                matchId,
                new BigDecimal("0.01000000"),
                new BigDecimal("65000.00000000"),
                Fee.zero("USDT"),
                RESERVED_AMOUNT,
                REALIZED_PNL,
                true
        ));
    }

    private MarginReleaseEvent marginReleaseEvent(UUID transactionId, UUID runnerId) {
        return new MarginReleaseEvent(MarginRelease.fullRelease(
                transactionId,
                runnerId,
                RESERVED_AMOUNT,
                ReleaseReason.EXPIRED
        ));
    }

    private DeadLetterEntry awaitUnresolvedEntry(UUID portfolioId,
                                                 UUID runnerId,
                                                 String eventTypeFragment,
                                                 String transactionIdFragment,
                                                 Duration timeout) {
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            DeadLetterEntry entry = deadLetterEntryRepository.findUnresolved(portfolioId, runnerId, 20).stream()
                    .filter(candidate -> candidate.getRawPayload().contains(eventTypeFragment))
                    .filter(candidate -> candidate.getRawPayload().contains(transactionIdFragment))
                    .findFirst()
                    .orElse(null);
            if (entry != null) {
                return entry;
            }
            sleep(80);
        }
        throw new AssertionError("Timeout waiting unresolved DLQ entry eventType="
                + eventTypeFragment + " transactionId=" + transactionIdFragment);
    }

    private void assertResolved(UUID deadLetterId, String resolvedBy) {
        DeadLetterEntry entry = deadLetterEntryRepository.findById(deadLetterId)
                .orElseThrow(() -> new IllegalStateException("Dead letter not found: " + deadLetterId));
        assertTrue(entry.isResolved());
        assertEquals(resolvedBy, entry.getResolvedBy());
        assertNotNull(entry.getResolvedAt());
    }

    private void assertBalance(UUID portfolioId,
                               BigDecimal expectedAvailable,
                               BigDecimal expectedReserved,
                               BigDecimal expectedRealized) {
        GlobalBalance balance = awaitBalance(portfolioId, expectedAvailable, expectedReserved, expectedRealized, WAIT_TIMEOUT);
        assertEquals(0, balance.getAvailableBalance().compareTo(expectedAvailable));
        assertEquals(0, balance.getReservedBalance().compareTo(expectedReserved));
        assertEquals(0, balance.getRealizedBalance().compareTo(expectedRealized));
    }

    private GlobalBalance awaitBalance(UUID portfolioId,
                                       BigDecimal expectedAvailable,
                                       BigDecimal expectedReserved,
                                       BigDecimal expectedRealized,
                                       Duration timeout) {
        Instant deadline = Instant.now().plus(timeout);
        GlobalBalance last = null;
        while (Instant.now().isBefore(deadline)) {
            last = globalBalanceRepository.findByPortfolioId(portfolioId).orElse(null);
            if (last != null
                    && last.getAvailableBalance().compareTo(expectedAvailable) == 0
                    && last.getReservedBalance().compareTo(expectedReserved) == 0
                    && last.getRealizedBalance().compareTo(expectedRealized) == 0) {
                return last;
            }
            sleep(80);
        }
        throw new AssertionError("Timeout waiting global balance portfolioId=" + portfolioId
                + " expectedAvailable=" + expectedAvailable
                + " expectedReserved=" + expectedReserved
                + " expectedRealized=" + expectedRealized
                + " lastAvailable=" + (last == null ? "null" : last.getAvailableBalance())
                + " lastReserved=" + (last == null ? "null" : last.getReservedBalance())
                + " lastRealized=" + (last == null ? "null" : last.getRealizedBalance()));
    }

    private void assertCapitalLedgerCount(String eventType, UUID eventId, int expectedCount) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM capital_event_ledger WHERE event_type = ? AND event_id = ?",
                Integer.class,
                eventType,
                eventId
        );
        assertEquals(expectedCount, count == null ? 0 : count);
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


