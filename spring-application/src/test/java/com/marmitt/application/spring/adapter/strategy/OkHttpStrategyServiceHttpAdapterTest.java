package com.marmitt.application.spring.adapter.strategy;

import com.marmitt.strategyservice.StrategyServiceHttpPort;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * T41: o transporte OkHttp entrega toda falha como IOException — inclusive timeout no meio da leitura
 * do corpo, que o cliente precisa classificar como UNREACHABLE.
 */
class OkHttpStrategyServiceHttpAdapterTest {

    private final MockWebServer server = new MockWebServer();
    private OkHttpStrategyServiceHttpAdapter adapter;

    @BeforeEach
    void setUp() throws IOException {
        server.start();
        adapter = new OkHttpStrategyServiceHttpAdapter(new OkHttpClient.Builder()
                .callTimeout(Duration.ofMillis(300))
                .build());
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void postsJsonAndReturnsStatusAndBody() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"ok\":true}"));

        StrategyServiceHttpPort.Response response = adapter.postJson(server.url("/v1/evaluate").toString(),
                Map.of("Authorization", "Bearer t"), "{}");

        assertEquals(200, response.statusCode());
        assertEquals("{\"ok\":true}", response.body());
        assertEquals("Bearer t", server.takeRequest().getHeader("Authorization"));
    }

    @Test
    void timeoutBeforeTheHeadersIsAnIOException() {
        server.enqueue(new MockResponse().setBody("{}").setHeadersDelay(2, TimeUnit.SECONDS));

        assertThrows(IOException.class, () -> adapter.postJson(server.url("/v1/evaluate").toString(), Map.of(), "{}"));
    }

    @Test
    void timeoutWhileReadingTheBodyIsAnIOException() {
        // Cabecalhos chegam na hora; o corpo chega a 1 byte por segundo, alem do callTimeout.
        server.enqueue(new MockResponse().setBody("{\"decision\":\"SHOULD_HOLD\"}").throttleBody(1, 1, TimeUnit.SECONDS));

        assertThrows(IOException.class, () -> adapter.postJson(server.url("/v1/evaluate").toString(), Map.of(), "{}"));
    }
}
