package com.marmitt.application.spring.adapter.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.marmitt.core.dto.notification.ErrorNotificationEvent;
import com.marmitt.core.ports.outbound.notification.ErrorNotificationPort;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;

/**
 * Reporta erros terminais para um webhook Discord como embed.
 *
 * <p><b>Tolerante a falha:</b> se {@code webhookUrl} for vazio o adapter é no-op; o envio é
 * assíncrono (fire-and-forget) e qualquer falha de rede/HTTP é apenas logada — nunca propaga
 * exceção nem bloqueia o caller (boot/DLQ).
 */
@Slf4j
public class DiscordWebhookNotificationAdapter implements ErrorNotificationPort {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final String webhookUrl;
    private final String grafanaExploreTemplate;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public DiscordWebhookNotificationAdapter(String webhookUrl, String grafanaExploreTemplate,
                                             ObjectMapper objectMapper, HttpClient httpClient) {
        this.webhookUrl = webhookUrl;
        this.grafanaExploreTemplate = grafanaExploreTemplate;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    @Override
    public void notifyError(ErrorNotificationEvent event) {
        if (webhookUrl == null || webhookUrl.isBlank()) {
            return; // Discord não configurado — no-op
        }
        try {
            String payload = buildPayload(event);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(webhookUrl))
                    .timeout(TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .whenComplete((response, error) -> {
                        if (error != null) {
                            log.error("discord notification failed type={} title={} reason={}",
                                    event.type(), event.title(), error.getMessage());
                        } else if (response.statusCode() >= 300) {
                            log.error("discord notification rejected type={} status={} body={}",
                                    event.type(), response.statusCode(), response.body());
                        }
                    });
        } catch (Exception e) {
            // Montagem do payload não deve derrubar o fluxo de erro principal.
            log.error("discord notification error type={} title={} reason={}",
                    event.type(), event.title(), e.getMessage());
        }
    }

    private String buildPayload(ErrorNotificationEvent event) throws Exception {
        ObjectNode embed = objectMapper.createObjectNode();
        embed.put("title", "[" + event.type() + "] " + event.title());
        if (event.description() != null) {
            embed.put("description", event.description());
        }
        String lokiLink = lokiLink(event.correlationId());
        if (lokiLink != null) {
            embed.put("url", lokiLink);
        }
        if (event.occurredAt() != null) {
            embed.put("timestamp", event.occurredAt().toString());
        }

        ArrayNode fields = embed.putArray("fields");
        addField(fields, "Reason", event.type().name());
        addField(fields, "Runner", asText(event.runnerId()));
        addField(fields, "DLQ ID", asText(event.dlqId()));
        addField(fields, "CorrelationId", event.correlationId());

        ObjectNode root = objectMapper.createObjectNode();
        root.putArray("embeds").add(embed);
        return objectMapper.writeValueAsString(root);
    }

    private void addField(ArrayNode fields, String name, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        ObjectNode field = fields.addObject();
        field.put("name", name);
        field.put("value", value);
        field.put("inline", true);
    }

    private String lokiLink(String correlationId) {
        if (grafanaExploreTemplate == null || grafanaExploreTemplate.isBlank() || correlationId == null) {
            return null;
        }
        return grafanaExploreTemplate.replace("{correlationId}", correlationId);
    }

    private static String asText(UUID value) {
        return value == null ? null : value.toString();
    }
}
