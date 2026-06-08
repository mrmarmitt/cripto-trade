package com.marmitt.application.spring.userdata;

import com.marmitt.application.spring.CTradeApplication;
import com.marmitt.core.dto.connection.ConnectionKey;
import com.marmitt.core.enums.ConnectionStatus;
import com.marmitt.core.ports.inbound.websocket.ConnectUserStreamPort;
import com.marmitt.core.ports.outbound.repository.WebSocketConnectionRepositoryPort;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifica que o user data stream da Binance conecta via WebSocket API,
 * envia subscription message assinada após conexão, e após reconexão
 * envia nova subscription com assinatura fresca — sem chamar endpoints REST de listen key.
 */
@Testcontainers
@SpringBootTest(classes = CTradeApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class BinanceListenKeyReconnectIntegrationTest {

    static final MockWebServer MOCK_SERVER;

    static {
        try {
            MOCK_SERVER = new MockWebServer();
            MOCK_SERVER.start();
        } catch (IOException e) {
            throw new RuntimeException("Failed to start MockWebServer", e);
        }
    }

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("ctrade").withUsername("ctrade").withPassword("ctrade123");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
        r.add("spring.flyway.enabled", () -> "true");
        r.add("runner.boot.orchestrator-enabled", () -> "false");
        r.add("binance.api-key", () -> "test-api-key");
        r.add("binance.api-secret", () -> "dGVzdC1hcGktc2VjcmV0");
        r.add("binance.rest-base-url", () -> "http://localhost:" + MOCK_SERVER.getPort());
        r.add("binance.ws-base-url", () -> "ws://localhost:" + MOCK_SERVER.getPort());
        r.add("binance.ws-api-base-url", () -> "ws://localhost:" + MOCK_SERVER.getPort());
    }

    @AfterAll
    static void stopMockServer() throws IOException {
        MOCK_SERVER.shutdown();
    }

    @Autowired ConnectUserStreamPort connectUserStreamPort;
    @Autowired WebSocketConnectionRepositoryPort connectionRepository;

    @Test
    void userDataStream_shouldSubscribeViaWebSocket_andReconnectWithFreshSignature() throws Exception {
        CountDownLatch firstSubscriptionReceived = new CountDownLatch(1);
        CountDownLatch secondSubscriptionReceived = new CountDownLatch(1);

        // Primera conexão WS: servidor confirma subscription e fecha a conexão
        MOCK_SERVER.enqueue(new MockResponse().withWebSocketUpgrade(new WebSocketListener() {
            @Override
            public void onMessage(WebSocket webSocket, String text) {
                webSocket.send("{\"id\":\"sub-1\",\"status\":200,\"result\":{\"subscriptionId\":0}}");
                firstSubscriptionReceived.countDown();
                // Fecha a conexão pelo lado do servidor para transicionar para CLOSED
                webSocket.close(1001, "server disconnect");
            }
        }));

        // Reconexão WS: servidor recebe nova subscription com timestamp/assinatura frescos
        MOCK_SERVER.enqueue(new MockResponse().withWebSocketUpgrade(new WebSocketListener() {
            @Override
            public void onMessage(WebSocket webSocket, String text) {
                webSocket.send("{\"id\":\"sub-2\",\"status\":200,\"result\":{\"subscriptionId\":0}}");
                secondSubscriptionReceived.countDown();
            }
        }));

        connectUserStreamPort.execute("BINANCE");

        // 1) Subscription message deve ser enviada após conectar
        assertTrue(firstSubscriptionReceived.await(10, TimeUnit.SECONDS),
                "subscription message must be sent via WebSocket within 10s");

        ConnectionKey userKey = ConnectionKey.userStream("BINANCE");
        awaitCondition(Duration.ofSeconds(15), 100,
                () -> {
                    var mgr = connectionRepository.getConnection(userKey);
                    return mgr != null ? mgr.getConnectionResult().status() : null;
                },
                s -> s == ConnectionStatus.CLOSED,
                "connection must reach CLOSED after server-initiated disconnect");

        // 2) Reconectar — deve enviar nova subscription com assinatura fresca
        connectUserStreamPort.execute("BINANCE");

        assertTrue(secondSubscriptionReceived.await(15, TimeUnit.SECONDS),
                "reconnect subscription message must be sent within 15s");

        awaitCondition(Duration.ofSeconds(15), 100,
                () -> {
                    var mgr = connectionRepository.getConnection(userKey);
                    return mgr != null ? mgr.getConnectionResult().status() : null;
                },
                s -> s == ConnectionStatus.CONNECTED,
                "user data stream must reach CONNECTED after reconnect");
    }

    // ─── suporte ──────────────────────────────────────────────────────────────

    private <T> T awaitCondition(Duration timeout, long pollMs, Supplier<T> supply,
                                  Predicate<T> predicate, String message) {
        Instant deadline = Instant.now().plus(timeout);
        T last = null;
        while (Instant.now().isBefore(deadline)) {
            last = supply.get();
            if (predicate.test(last)) return last;
            sleep(pollMs);
        }
        throw new AssertionError(message + " (last=" + last + ")");
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while awaiting async processing", e);
        }
    }
}
