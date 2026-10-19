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
    void missingTokenGetsA401() throws Exception {
        assertEquals(401, post("{}", null).statusCode());
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
