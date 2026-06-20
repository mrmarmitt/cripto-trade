package com.marmitt.application.spring.config.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marmitt.application.spring.adapter.notification.DiscordWebhookNotificationAdapter;
import com.marmitt.core.ports.outbound.notification.ErrorNotificationPort;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Wiring das notificações ativas de erro (T21 — Camada 1).
 *
 * <p>Registra sempre um {@link ErrorNotificationPort}; quando o webhook não está configurado,
 * o adapter opera em modo no-op, então os listeners podem depender do port incondicionalmente.
 */
@Configuration
@EnableConfigurationProperties(NotificationProperties.class)
public class NotificationConfig {

    @Bean
    public HttpClient notificationHttpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @Bean
    public ErrorNotificationPort errorNotificationPort(NotificationProperties properties,
                                                       ObjectMapper objectMapper,
                                                       HttpClient notificationHttpClient) {
        return new DiscordWebhookNotificationAdapter(
                properties.getDiscord().getWebhookUrl(),
                properties.getGrafana().getExploreUrlTemplate(),
                objectMapper,
                notificationHttpClient);
    }
}
