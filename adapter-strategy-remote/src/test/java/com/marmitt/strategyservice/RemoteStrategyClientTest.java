package com.marmitt.strategyservice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marmitt.core.domain.Symbol;
import com.marmitt.core.dto.strategy.PositionContext;
import com.marmitt.core.dto.strategy.StrategyContextDto;
import com.marmitt.core.dto.strategy.StrategyInputDto;
import com.marmitt.core.dto.strategy.StrategyOutputDto;
import com.marmitt.core.enums.TradingAction;
import com.marmitt.core.exceptions.StrategyUnavailableException;
import com.marmitt.core.exceptions.StrategyUnavailableException.Reason;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T41: cliente do contrato — mapeamento, validacao, sem retry, decisao velha e sequence por runner.
 */
class RemoteStrategyClientTest {

    private static final UUID REF = UUID.fromString("5ea5e0a0-0000-4000-a000-000000000001");
    private static final Instant TICK = Instant.parse("2026-10-19T12:00:00Z");
    private static final String BUY = """
            {"strategyName":"SMA","decision":"SHOULD_BUY","confidence":"0.80","quantity":"0.00100000",
             "limitPrice":null,"targetLotId":null,"targetTransactionId":null,"reasoning":"cross"}""";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final FakeHttp http = new FakeHttp();

    @Test
    void validDecisionIsMappedToTheCore() {
        http.reply = request -> new StrategyServiceHttpPort.Response(200, BUY);

        StrategyOutputDto output = client(Duration.ofSeconds(1)).evaluate(REF, input(), context(UUID.randomUUID()));

        assertEquals(TradingAction.SHOULD_BUY, output.decision());
        assertEquals(0, new BigDecimal("0.001").compareTo(output.quantity()));
        assertNull(output.limitPrice());
        assertEquals("http://strategy:8090/v1/evaluate", http.calls.getFirst().url());
        assertEquals("Bearer token-1", http.calls.getFirst().headers().get("Authorization"));
    }

    @Test
    void requestCarriesTickIdAndAPerRunnerSequence() throws Exception {
        http.reply = request -> new StrategyServiceHttpPort.Response(200, BUY);
        RemoteStrategyClient client = client(Duration.ofSeconds(1));
        UUID runnerA = UUID.randomUUID();
        UUID runnerB = UUID.randomUUID();

        client.evaluate(REF, input(), context(runnerA));
        client.evaluate(REF, input(), context(runnerA));
        client.evaluate(REF, input(), context(runnerB));

        JsonNode first = objectMapper.readTree(http.calls.get(0).body());
        JsonNode second = objectMapper.readTree(http.calls.get(1).body());
        JsonNode third = objectMapper.readTree(http.calls.get(2).body());
        long firstSequence = first.path("sequence").asLong();
        assertEquals(firstSequence + 1, second.path("sequence").asLong(), "Estritamente crescente por runner");
        assertEquals(runnerA + ":" + TICK.toEpochMilli() + ":" + (firstSequence + 1), second.path("tickId").asText());
        assertEquals(firstSequence, third.path("sequence").asLong(), "Cada runner tem o seu contador");
        assertEquals("65000.50", second.path("input").path("currentPrice").asText(), "Decimal trafega como string");
    }

    @Test
    void sequenceKeepsGrowingAcrossAClientRestart() throws Exception {
        http.reply = request -> new StrategyServiceHttpPort.Response(200, BUY);
        UUID runner = UUID.randomUUID();
        RemoteStrategyClient beforeRestart = client(Duration.ofSeconds(1));
        for (int i = 0; i < 5; i++) {
            beforeRestart.evaluate(REF, input(), context(runner));
        }
        long lastBeforeRestart = objectMapper.readTree(http.calls.getLast().body()).path("sequence").asLong();

        // ctrade reinicia um segundo depois: contador em memoria recomeca, o servico guardou o anterior.
        RemoteStrategyClient afterRestart = new RemoteStrategyClient(http, objectMapper, endpoints(),
                Duration.ofSeconds(5), Clock.fixed(TICK.plusSeconds(1), ZoneOffset.UTC));
        afterRestart.evaluate(REF, input(), context(runner));

        long firstAfterRestart = objectMapper.readTree(http.calls.getLast().body()).path("sequence").asLong();
        assertTrue(firstAfterRestart > lastBeforeRestart,
                "Depois do restart o sequence continua acima do ultimo visto pelo servico");
    }

    @Test
    void nullMetaIsAnInvalidResponse() {
        RemoteStrategyClient client = client(Duration.ofSeconds(1));
        for (String body : new String[]{"null", "[null]"}) {
            http.reply = request -> new StrategyServiceHttpPort.Response(200, body);
            StrategyUnavailableException e = assertThrows(StrategyUnavailableException.class, () -> client.describe(REF));
            assertEquals(Reason.INVALID_RESPONSE, e.reason(), body);
        }
    }

    @Test
    void failuresBecomeStrategyUnavailableWithoutRetry() {
        RemoteStrategyClient client = client(Duration.ofSeconds(1));

        http.reply = request -> new StrategyServiceHttpPort.Response(503, "{}");
        assertReason(Reason.ERROR_RESPONSE, client);
        http.reply = request -> { throw new UncheckedIo(new IOException("timeout")); };
        assertReason(Reason.UNREACHABLE, client);
        http.reply = request -> new StrategyServiceHttpPort.Response(200, "not json");
        assertReason(Reason.INVALID_RESPONSE, client);
        http.reply = request -> new StrategyServiceHttpPort.Response(200, BUY.replace("\"0.00100000\"", "\"-1\""));
        assertReason(Reason.INVALID_RESPONSE, client);

        assertEquals(4, http.calls.size(), "Uma chamada por avaliacao: sem retry");
    }

    @Test
    void decisionOlderThanTheLimitIsDiscarded() {
        http.reply = request -> new StrategyServiceHttpPort.Response(200, BUY);
        RemoteStrategyClient client = new RemoteStrategyClient(http, objectMapper, endpoints(),
                Duration.ofMillis(500), Clock.fixed(TICK.plusMillis(800), ZoneOffset.UTC));

        StrategyUnavailableException e = assertThrows(StrategyUnavailableException.class,
                () -> client.evaluate(REF, input(), context(UUID.randomUUID())));
        assertEquals(Reason.STALE_DECISION, e.reason());
    }

    @Test
    void unknownStrategyRefHasNoEndpoint() {
        StrategyUnavailableException e = assertThrows(StrategyUnavailableException.class,
                () -> client(Duration.ofSeconds(1)).evaluate(UUID.randomUUID(), input(), context(UUID.randomUUID())));
        assertEquals(Reason.UNREACHABLE, e.reason());
    }

    @Test
    void describeReadsTheServedIdentityFromMeta() {
        http.reply = request -> new StrategyServiceHttpPort.Response(200,
                "[{\"strategyId\":\"" + REF + "\",\"name\":\"SMA\",\"version\":\"1.0.0\",\"contractVersion\":\"1.1.0\"}]");

        var identity = client(Duration.ofSeconds(1)).describe(REF);

        assertEquals(REF, identity.strategyId());
        assertEquals("1.1.0", identity.contractVersion());
        assertEquals("http://strategy:8090/v1/meta?strategyRef=" + REF, http.calls.getFirst().url());
    }

    private void assertReason(Reason expected, RemoteStrategyClient client) {
        StrategyUnavailableException e = assertThrows(StrategyUnavailableException.class,
                () -> client.evaluate(REF, input(), context(UUID.randomUUID())));
        assertEquals(expected, e.reason());
    }

    private RemoteStrategyClient client(Duration maxAge) {
        return new RemoteStrategyClient(http, objectMapper, endpoints(), maxAge,
                Clock.fixed(TICK.plusMillis(100), ZoneOffset.UTC));
    }

    private static List<RemoteStrategyEndpoint> endpoints() {
        return List.of(new RemoteStrategyEndpoint(REF, "http://strategy:8090/v1/", "token-1"));
    }

    private static StrategyInputDto input() {
        BigDecimal price = new BigDecimal("65000.50");
        return new StrategyInputDto(Symbol.of("BTCUSDT"), price, null, price, price, BigDecimal.ONE,
                price, price, TICK, Map.of());
    }

    private static StrategyContextDto context(UUID runnerId) {
        return StrategyContextDto.builder()
                .runnerId(runnerId)
                .portfolioId(UUID.randomUUID())
                .symbol(Symbol.of("BTCUSDT"))
                .positionContext(PositionContext.empty("BTCUSDT"))
                .openLots(List.of())
                .pendingOrders(List.of())
                .totalCapital(new BigDecimal("10000"))
                .availableCapital(new BigDecimal("10000"))
                .maxOperationAmount(new BigDecimal("2000"))
                .minOperationAmount(new BigDecimal("10"))
                .maxOpenPositions(1)
                .currentOpenPositions(0)
                .realizedPnl(BigDecimal.ZERO)
                .unrealizedPnl(BigDecimal.ZERO)
                .build();
    }

    private record Call(String url, Map<String, String> headers, String body) {
    }

    private static final class UncheckedIo extends RuntimeException {
        private UncheckedIo(IOException cause) {
            super(cause);
        }
    }

    private static final class FakeHttp implements StrategyServiceHttpPort {
        private final List<Call> calls = new ArrayList<>();
        private Function<Call, Response> reply;

        @Override
        public Response get(String url, Map<String, String> headers) throws IOException {
            return answer(new Call(url, headers, null));
        }

        @Override
        public Response postJson(String url, Map<String, String> headers, String body) throws IOException {
            return answer(new Call(url, headers, body));
        }

        private Response answer(Call call) throws IOException {
            calls.add(call);
            try {
                return reply.apply(call);
            } catch (UncheckedIo e) {
                throw (IOException) e.getCause();
            }
        }
    }
}
