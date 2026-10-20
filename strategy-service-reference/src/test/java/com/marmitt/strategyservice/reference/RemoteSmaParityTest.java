package com.marmitt.strategyservice.reference;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marmitt.core.domain.strategy.StrategyEndpoint;
import com.marmitt.core.domain.Symbol;
import com.marmitt.core.dto.strategy.OpenLotDto;
import com.marmitt.core.dto.strategy.PositionContext;
import com.marmitt.core.dto.strategy.StrategyContextDto;
import com.marmitt.core.dto.strategy.StrategyInputDto;
import com.marmitt.core.dto.strategy.StrategyOutputDto;
import com.marmitt.core.ports.outbound.strategy.StrategyEvaluationPort.RemoteStrategyIdentity;
import com.marmitt.strategy.impl.simple_moving_avager.SimpleMovingAverageStrategy;
import com.marmitt.strategyservice.RemoteStrategyClient;
import com.marmitt.strategyservice.StrategyServiceHttpPort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T41 — portão da migração: o mesmo replay de ticks no SMA in-process e no SMA servido pelo contrato
 * (HTTP real, app de referência) produz a mesma sequência de decisões, por runner.
 */
class RemoteSmaParityTest {

    private static final UUID REF = ReferenceStrategyServer.DEFAULT_STRATEGY_REF;

    private ReferenceStrategyServer server;
    private RemoteStrategyClient client;
    private StrategyEndpoint endpoint;

    @BeforeEach
    void startServer() throws IOException {
        server = new ReferenceStrategyServer(REF, new SimpleMovingAverageStrategy(), "secret");
        server.start(0);
        endpoint = new StrategyEndpoint("http://localhost:" + server.port() + "/v1", "secret");
        client = new RemoteStrategyClient(new JdkHttp(), new ObjectMapper(), Duration.ofSeconds(5), Clock.systemUTC());
    }

    @AfterEach
    void stopServer() {
        server.stop();
    }

    @Test
    void remoteSmaDecidesExactlyLikeInProcessSmaForEachRunner() {
        SimpleMovingAverageStrategy inProcess = new SimpleMovingAverageStrategy();
        UUID runnerA = UUID.randomUUID();
        UUID runnerB = UUID.randomUUID();
        List<String> local = new ArrayList<>();
        List<String> remote = new ArrayList<>();

        for (int i = 0; i < 80; i++) {
            // Dois runners intercalados, com series diferentes: o warm-up nao pode se misturar.
            for (UUID runner : List.of(runnerA, runnerB)) {
                BigDecimal price = price(runner.equals(runnerA) ? i : i + 13);
                StrategyInputDto input = input(price);
                StrategyContextDto context = context(runner);
                local.add(summary(inProcess.executeStrategy(input, context)));
                remote.add(summary(client.evaluate(REF, endpoint, input, context)));
            }
        }

        assertEquals(local, remote);
        assertTrue(local.stream().anyMatch(decision -> decision.startsWith("SHOULD_BUY")),
                "O replay precisa exercitar ao menos um BUY para a paridade valer");
    }

    @Test
    void metaDeclaresTheServedIdentity() {
        RemoteStrategyIdentity identity = client.describe(REF, endpoint);

        assertEquals(REF, identity.strategyId());
        assertEquals("SimpleMovingAverageStrategy", identity.name());
        assertEquals(ReferenceStrategyServer.CONTRACT_VERSION, identity.contractVersion());
    }

    private static String summary(StrategyOutputDto output) {
        return output.decision() + " qty=" + output.quantity().stripTrailingZeros().toPlainString()
                + " conf=" + output.confidence().stripTrailingZeros().toPlainString()
                + " lot=" + output.targetLotId();
    }

    /** Onda de preco que cruza a media varias vezes. */
    private static BigDecimal price(int step) {
        double wave = Math.sin(step / 4.0) * 400;
        return BigDecimal.valueOf(65000 + Math.round(wave));
    }

    private static StrategyInputDto input(BigDecimal price) {
        return new StrategyInputDto(Symbol.of("BTCUSDT"), price, null, price, price, BigDecimal.TEN,
                price, price, Instant.now(), Map.of());
    }

    private static StrategyContextDto context(UUID runnerId) {
        return StrategyContextDto.builder()
                .runnerId(runnerId)
                .portfolioId(UUID.fromString("22222222-2222-2222-2222-222222222222"))
                .symbol(Symbol.of("BTCUSDT"))
                .positionContext(PositionContext.empty("BTCUSDT"))
                .openLots(List.<OpenLotDto>of())
                .pendingOrders(List.of())
                .totalCapital(new BigDecimal("10000"))
                .availableCapital(new BigDecimal("10000"))
                .maxOperationAmount(new BigDecimal("10000"))
                .minOperationAmount(new BigDecimal("10"))
                .maxOpenPositions(1)
                .currentOpenPositions(0)
                .realizedPnl(BigDecimal.ZERO)
                .unrealizedPnl(BigDecimal.ZERO)
                .build();
    }

    /** Transporte de teste com o cliente HTTP do JDK (em producao: OkHttp na composicao). */
    private static final class JdkHttp implements StrategyServiceHttpPort {
        private final HttpClient http = HttpClient.newHttpClient();

        @Override
        public Response get(String url, Map<String, String> headers) throws IOException {
            return send(HttpRequest.newBuilder(URI.create(url)).GET(), headers);
        }

        @Override
        public Response postJson(String url, Map<String, String> headers, String body) throws IOException {
            return send(HttpRequest.newBuilder(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body)), headers);
        }

        private Response send(HttpRequest.Builder builder, Map<String, String> headers) throws IOException {
            headers.forEach(builder::header);
            try {
                HttpResponse<String> response = http.send(builder.timeout(Duration.ofSeconds(5)).build(),
                        HttpResponse.BodyHandlers.ofString());
                return new Response(response.statusCode(), response.body());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException(e);
            }
        }
    }
}
