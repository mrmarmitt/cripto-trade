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
import okhttp3.mockwebserver.RecordedRequest;
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
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Verifica que o user data stream da Binance reconecta automaticamente após falha
 * no WebSocket e obtém um novo listen key via REST.
 *
 * Usa MockWebServer para simular os endpoints REST e WebSocket da Binance sem
 * depender de credenciais ou conexão real.
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
        r.add("binance.api-secret", () -> "test-api-secret");
        r.add("binance.rest-base-url", () -> "http://localhost:" + MOCK_SERVER.getPort());
        r.add("binance.ws-base-url", () -> "ws://localhost:" + MOCK_SERVER.getPort());
    }

    @AfterAll
    static void stopMockServer() throws IOException {
        MOCK_SERVER.shutdown();
    }

    @Autowired ConnectUserStreamPort connectUserStreamPort;
    @Autowired WebSocketConnectionRepositoryPort connectionRepository;

    @Test
    void userDataStream_shouldReconnectAndObtainNewListenKey_afterWebSocketFailure() throws Exception {
        // POST inicial → listen key 1
        MOCK_SERVER.enqueue(listenKeyResponse("test-key-1"));
        // WS inicial → resposta 500 garante onFailure no cliente (cancel() pode disparar onClosed)
        MOCK_SERVER.enqueue(new MockResponse().setResponseCode(500));
        // DELETE → revogar listen key 1 no início do reconnect
        MOCK_SERVER.enqueue(new MockResponse().setBody("{}").setHeader("Content-Type", "application/json"));
        // POST reconexão → listen key 2
        MOCK_SERVER.enqueue(listenKeyResponse("test-key-2"));
        // WS reconexão → aceita e mantém aberto
        MOCK_SERVER.enqueue(wsUpgradeAndKeepOpen());

        connectUserStreamPort.execute("BINANCE");

        // Verifica a sequência de requisições usando takeRequest com timeouts generosos.
        // A primeira falha (500) dispara reconnect com 5s de delay; os timeouts abaixo
        // cobrem toda a sequência sem depender de polling de estado.
        RecordedRequest req1 = MOCK_SERVER.takeRequest(10, TimeUnit.SECONDS);
        assertNotNull(req1, "expected initial POST /api/v3/userDataStream");
        assertEquals("POST", req1.getMethod());
        assertEquals("/api/v3/userDataStream", req1.getPath());

        RecordedRequest req2 = MOCK_SERVER.takeRequest(5, TimeUnit.SECONDS);
        assertNotNull(req2, "expected initial WS upgrade request (returns 500)");

        // ConnectionFailedHandler agenda reconnect com 5s de delay
        RecordedRequest req3 = MOCK_SERVER.takeRequest(15, TimeUnit.SECONDS);
        assertNotNull(req3, "expected DELETE to revoke listen key before reconnect");
        assertEquals("DELETE", req3.getMethod());

        RecordedRequest req4 = MOCK_SERVER.takeRequest(5, TimeUnit.SECONDS);
        assertNotNull(req4, "expected POST for new listen key on reconnect");
        assertEquals("POST", req4.getMethod());
        assertEquals("/api/v3/userDataStream", req4.getPath());

        // Após reconexão bem-sucedida, o status deve ser CONNECTED
        ConnectionKey userKey = ConnectionKey.userStream("BINANCE");
        awaitCondition(Duration.ofSeconds(10), 200,
                () -> {
                    var mgr = connectionRepository.getConnection(userKey);
                    return mgr != null ? mgr.getConnectionResult().status() : null;
                },
                s -> s == ConnectionStatus.CONNECTED,
                "user data stream must reach CONNECTED after automatic reconnect");
    }

    // ─── suporte ─────────────────────────────────────────────────────────

    private static MockResponse listenKeyResponse(String listenKey) {
        return new MockResponse()
                .setBody("{\"listenKey\":\"" + listenKey + "\"}")
                .setHeader("Content-Type", "application/json");
    }

    private static MockResponse wsUpgradeAndKeepOpen() {
        return new MockResponse().withWebSocketUpgrade(new WebSocketListener() {
            // sem ação: mantém a conexão aberta para que onOpen do cliente dispare
        });
    }

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
