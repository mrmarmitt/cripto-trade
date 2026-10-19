package com.marmitt.strategyservice.reference;

import com.marmitt.strategy.impl.simple_moving_avager.SimpleMovingAverageStrategy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T41: a app de referencia responde 400 com corpo JSON para payload invalido e 401 sem token.
 */
class ReferenceStrategyServerTest {

    private final HttpClient http = HttpClient.newHttpClient();
    private ReferenceStrategyServer server;

    @BeforeEach
    void start() throws Exception {
        server = new ReferenceStrategyServer(ReferenceStrategyServer.DEFAULT_STRATEGY_REF,
                new SimpleMovingAverageStrategy(), "secret");
        server.start(0);
    }

    @AfterEach
    void stop() {
        server.stop();
    }

    @Test
    void malformedPayloadGetsA400WithJsonError() throws Exception {
        HttpResponse<String> response = post("{not json", "secret");

        assertEquals(400, response.statusCode());
        assertTrue(response.body().contains("\"message\""), response.body());
    }

    @Test
    void firstTickWithSequenceZeroIsEvaluated() throws Exception {
        String runner = java.util.UUID.randomUUID().toString();
        HttpResponse<String> response = post(evaluateRequest(runner, "t0", 0), "secret");

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("Aguardando historico: 1/"),
                "sequence 0 entra no warm-up (primeiro tick do historico): " + response.body());
    }

    @Test
    void missingTokenGetsA401() throws Exception {
        assertEquals(401, post("{}", null).statusCode());
    }

    private static String evaluateRequest(String runnerId, String tickId, long sequence) {
        String lots = "[]";
        return "{\"strategyRef\":\"" + ReferenceStrategyServer.DEFAULT_STRATEGY_REF + "\",\"tickId\":\"" + tickId
                + "\",\"sequence\":" + sequence + ",\"input\":{\"symbol\":\"BTCUSDT\",\"currentPrice\":\"65000\","
                + "\"timestamp\":\"2026-10-19T12:00:00Z\"},\"context\":{\"runnerId\":\"" + runnerId + "\","
                + "\"portfolioId\":\"22222222-2222-2222-2222-222222222222\",\"symbol\":\"BTCUSDT\","
                + "\"positionContext\":{\"symbol\":\"BTCUSDT\",\"quantity\":\"0\",\"averagePrice\":\"0\","
                + "\"currentPrice\":\"0\",\"unrealizedPnl\":\"0\",\"realizedPnl\":\"0\",\"openLots\":" + lots + "},"
                + "\"openLots\":" + lots + ",\"pendingOrders\":[],\"totalCapital\":\"10000\",\"availableCapital\":\"10000\","
                + "\"maxOperationAmount\":\"2000\",\"minOperationAmount\":\"10\",\"maxOpenPositions\":1,"
                + "\"currentOpenPositions\":0,\"realizedPnl\":\"0\",\"unrealizedPnl\":\"0\"}}";
    }

    private HttpResponse<String> post(String body, String token) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(
                        URI.create("http://localhost:" + server.port() + "/v1/evaluate"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
