package com.marmitt.application.spring.bootstrap;

import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T41 — runner operando com estrategia remota pelo pipeline real. O strategy service e um
 * MockWebServer registrado pelo catalogo (conferido via /v1/meta). Decisao valida vira ordem;
 * 5xx, timeout, decisao fora do contrato e decisao velha viram HOLD com STRATEGY_UNAVAILABLE.
 */
class RemoteStrategyE2ETest extends ScenarioStrategyE2ETestSupport {

    private static final UUID REF = UUID.fromString("5ea5e0a0-0000-4000-a000-0000000000e2");
    private static final MockWebServer SERVICE = new MockWebServer();
    private static volatile Mode mode = Mode.BUY;

    enum Mode { BUY, ERROR, SLOW, STALE, MALFORMED }

    static {
        SERVICE.setDispatcher(new Dispatcher() {
            @NotNull
            @Override
            public MockResponse dispatch(@NotNull RecordedRequest request) {
                String path = request.getPath() == null ? "" : request.getPath();
                if (path.startsWith("/v1/meta")) {
                    return json("[{\"strategyId\":\"" + REF + "\",\"name\":\"RemoteE2E\",\"version\":\"1.0.0\","
                            + "\"contractVersion\":\"1.1.0\"}]");
                }
                BigDecimal limit = MARKET_PRICE.multiply(new BigDecimal("1.01"));
                String buy = "{\"strategyName\":\"RemoteE2E\",\"decision\":\"SHOULD_BUY\",\"confidence\":\"0.9\","
                        + "\"quantity\":\"0.00100000\",\"limitPrice\":\"" + limit.toPlainString() + "\",\"reasoning\":\"e2e\"}";
                return switch (mode) {
                    case BUY -> json(buy);
                    case ERROR -> new MockResponse().setResponseCode(503).setBody("{\"message\":\"down\"}");
                    case SLOW -> json(buy).setBodyDelay(2, TimeUnit.SECONDS);
                    case STALE -> json(buy).setBodyDelay(600, TimeUnit.MILLISECONDS);
                    case MALFORMED -> json(buy.replace("\"0.00100000\"", "\"-1\""));
                };
            }
        });
        try {
            SERVICE.start();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void remoteCatalog(DynamicPropertyRegistry registry) {
        registry.add("ctrade.strategy.remote.timeout-ms", () -> "1500");
        registry.add("ctrade.strategy.remote.max-decision-age-ms", () -> "400");
        registry.add("ctrade.strategy.remote.catalog[0].strategy-ref", REF::toString);
        registry.add("ctrade.strategy.remote.catalog[0].base-url", () -> SERVICE.url("/v1").toString());
        registry.add("ctrade.strategy.remote.catalog[0].name", () -> "RemoteE2E");
        registry.add("ctrade.strategy.remote.catalog[0].version", () -> "1.0.0");
    }

    @AfterAll
    static void stopService() throws IOException {
        SERVICE.shutdown();
    }

    @Test
    void remoteDecisionBecomesAnOrderThroughTheNormalPipeline() {
        mode = Mode.BUY;
        awaitCondition(() -> strategyRepository.findById(REF).isPresent(), WAIT_TIMEOUT, "estrategia remota registrada");
        UUID runnerId = createAndActivateRunner(createPortfolio(), REF);

        driveTicks(6);
        awaitCondition(() -> transactionsOfType(runnerId, "BUY").stream().anyMatch(tx -> "FILLED".equals(tx.status())),
                WAIT_TIMEOUT, "BUY decidida pela estrategia remota e preenchida", runnerId);

        assertTrue(signalEvaluatedCount(runnerId, "BUY") >= 1.0);
        assertEquals(0.0, signalEvaluatedCount(runnerId, "STRATEGY_UNAVAILABLE"));
    }

    @Test
    void unusableRemoteAnswersBecomeHoldWithStrategyUnavailable() {
        awaitCondition(() -> strategyRepository.findById(REF).isPresent(), WAIT_TIMEOUT, "estrategia remota registrada");
        for (Mode failure : new Mode[]{Mode.ERROR, Mode.SLOW, Mode.STALE, Mode.MALFORMED}) {
            mode = failure;
            UUID runnerId = createAndActivateRunner(createPortfolio(), REF);
            double before = signalEvaluatedCount(runnerId, "STRATEGY_UNAVAILABLE");

            publishTick(MARKET_PRICE);
            awaitCondition(() -> signalEvaluatedCount(runnerId, "STRATEGY_UNAVAILABLE") > before, WAIT_TIMEOUT,
                    failure + " vira STRATEGY_UNAVAILABLE", runnerId);

            assertEquals(0, transactionCount(runnerId), failure + " nao pode gerar ordem");
            assertTrue(remoteFailures(failure) >= 1.0, failure + " deve contar em strategy.remote.failures");
        }
    }

    private double remoteFailures(Mode failure) {
        String reason = switch (failure) {
            case ERROR -> "ERROR_RESPONSE";
            case SLOW -> "UNREACHABLE";
            case STALE -> "STALE_DECISION";
            case MALFORMED -> "INVALID_RESPONSE";
            case BUY -> throw new IllegalArgumentException();
        };
        try {
            return meterRegistry.get("strategy.remote.failures")
                    .tags("strategyRef", REF.toString(), "reason", reason).counter().count();
        } catch (io.micrometer.core.instrument.search.MeterNotFoundException e) {
            return 0.0;
        }
    }

    private static MockResponse json(String body) {
        return new MockResponse().setHeader("Content-Type", "application/json").setBody(body);
    }
}
