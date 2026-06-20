package com.marmitt.application.spring.adapter.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marmitt.core.dto.notification.ErrorNotificationEvent;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class DiscordWebhookNotificationAdapterTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private MockWebServer server;
    private HttpClient httpClient;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        httpClient = HttpClient.newHttpClient();
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void notifyError_isNoOpWhenWebhookBlank() {
        DiscordWebhookNotificationAdapter adapter =
                new DiscordWebhookNotificationAdapter("", "", MAPPER, httpClient);

        adapter.notifyError(dlqEvent(UUID.randomUUID(), "abc-correlation"));

        assertThat(server.getRequestCount()).isZero();
    }

    @Test
    void notifyError_postsDiscordEmbedWhenConfigured() throws InterruptedException {
        server.enqueue(new MockResponse().setResponseCode(204));
        UUID dlqId = UUID.randomUUID();
        DiscordWebhookNotificationAdapter adapter = new DiscordWebhookNotificationAdapter(
                server.url("/webhook").toString(),
                "https://grafana.local/explore?cid={correlationId}",
                MAPPER, httpClient);

        adapter.notifyError(dlqEvent(dlqId, "corr-123"));

        RecordedRequest request = server.takeRequest(2, TimeUnit.SECONDS);
        assertThat(request).isNotNull();
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getHeader("Content-Type")).contains("application/json");
        String body = request.getBody().readUtf8();
        assertThat(body).contains("[DLQ_PERSISTED]");
        assertThat(body).contains(dlqId.toString());
        assertThat(body).contains("corr-123");
        assertThat(body).contains("https://grafana.local/explore?cid=corr-123");
    }

    @Test
    void notifyError_swallowsHttpErrorResponse() throws InterruptedException {
        server.enqueue(new MockResponse().setResponseCode(500));
        DiscordWebhookNotificationAdapter adapter = new DiscordWebhookNotificationAdapter(
                server.url("/webhook").toString(), "", MAPPER, httpClient);

        assertThatCode(() -> adapter.notifyError(dlqEvent(UUID.randomUUID(), null)))
                .doesNotThrowAnyException();

        // request foi enviada (falha tratada no callback async, sem propagar)
        assertThat(server.takeRequest(2, TimeUnit.SECONDS)).isNotNull();
    }

    @Test
    void notifyError_swallowsConnectionFailure() throws IOException {
        String deadUrl = server.url("/webhook").toString();
        server.shutdown(); // ninguém escutando → falha de conexão

        DiscordWebhookNotificationAdapter adapter =
                new DiscordWebhookNotificationAdapter(deadUrl, "", MAPPER, httpClient);

        assertThatCode(() -> adapter.notifyError(dlqEvent(UUID.randomUUID(), null)))
                .doesNotThrowAnyException();
    }

    private static ErrorNotificationEvent dlqEvent(UUID dlqId, String correlationId) {
        return ErrorNotificationEvent.dlqPersisted(
                "Capital event sent to DLQ",
                "MARGIN_RELEASE esgotou os retries: boom",
                correlationId,
                UUID.randomUUID(),
                dlqId,
                Instant.parse("2026-06-20T10:00:00Z"));
    }
}
