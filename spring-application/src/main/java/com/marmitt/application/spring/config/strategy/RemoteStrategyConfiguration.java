package com.marmitt.application.spring.config.strategy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marmitt.application.spring.adapter.strategy.OkHttpStrategyServiceHttpAdapter;
import com.marmitt.application.spring.metrics.MeteredStrategyEvaluationPort;
import com.marmitt.core.application.usecase.strategy.RegisterRemoteStrategiesUseCase;
import com.marmitt.core.dto.strategy.RemoteStrategyDefinition;
import com.marmitt.core.ports.inbound.strategy.RegisterRemoteStrategiesPort;
import com.marmitt.core.ports.outbound.repository.StrategyRepositoryPort;
import com.marmitt.core.ports.outbound.strategy.StrategyEvaluationPort;
import com.marmitt.strategyservice.RemoteStrategyClient;
import com.marmitt.strategyservice.RemoteStrategyEndpoint;
import io.micrometer.core.instrument.MeterRegistry;
import okhttp3.OkHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

/**
 * Composição das estratégias remotas (T41): transporte OkHttp com timeout rígido, cliente do contrato,
 * métricas e o registro a partir do catálogo estático. Só entradas {@code enabled} entram.
 */
@Configuration
public class RemoteStrategyConfiguration {

    @Bean
    public StrategyEvaluationPort strategyEvaluationPort(RemoteStrategyProperties properties,
                                                         ObjectMapper objectMapper,
                                                         MeterRegistry meterRegistry) {
        Duration timeout = Duration.ofMillis(properties.getTimeoutMs());
        OkHttpClient client = new OkHttpClient.Builder()
                .callTimeout(timeout)
                .connectTimeout(timeout)
                .retryOnConnectionFailure(false)
                .build();
        List<RemoteStrategyEndpoint> endpoints = enabled(properties).stream()
                .map(entry -> new RemoteStrategyEndpoint(entry.getStrategyRef(), entry.getBaseUrl(), properties.getToken()))
                .toList();
        RemoteStrategyClient remote = new RemoteStrategyClient(new OkHttpStrategyServiceHttpAdapter(client),
                objectMapper, endpoints, Duration.ofMillis(properties.getMaxDecisionAgeMs()), Clock.systemUTC());
        return new MeteredStrategyEvaluationPort(remote, meterRegistry);
    }

    @Bean
    public RegisterRemoteStrategiesPort registerRemoteStrategies(StrategyRepositoryPort strategyRepository,
                                                                 StrategyEvaluationPort strategyEvaluationPort) {
        return new RegisterRemoteStrategiesUseCase(strategyRepository, strategyEvaluationPort);
    }

    @Bean
    public RemoteStrategyCatalog remoteStrategyCatalog(RemoteStrategyProperties properties) {
        return new RemoteStrategyCatalog(enabled(properties).stream()
                .map(entry -> new RemoteStrategyDefinition(entry.getStrategyRef(), entry.getName(), entry.getVersion()))
                .toList());
    }

    private static List<RemoteStrategyProperties.Entry> enabled(RemoteStrategyProperties properties) {
        return properties.getCatalog().stream().filter(RemoteStrategyProperties.Entry::isEnabled).toList();
    }

    /** Definições do catálogo habilitadas, na forma que o core entende. */
    public record RemoteStrategyCatalog(List<RemoteStrategyDefinition> definitions) {
    }
}
