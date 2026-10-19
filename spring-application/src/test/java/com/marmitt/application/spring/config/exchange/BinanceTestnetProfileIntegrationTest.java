package com.marmitt.application.spring.config.exchange;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marmitt.application.spring.repository.InMemoryExchangeAdapterRepository;
import com.marmitt.application.spring.repository.InMemoryWebSocketPortRegistry;
import com.marmitt.core.ports.outbound.events.EventPublisherPort;
import com.marmitt.core.ports.outbound.repository.ExchangeAdapterRepositoryPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = BinanceTestnetProfileIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "binance.api-key=test-key",
                "binance.api-secret=test-secret"
        }
)
@ActiveProfiles("testnet")
class BinanceTestnetProfileIntegrationTest {

    @Autowired
    private ExchangeAdapterRepositoryPort exchangeAdapterRepository;

    @Autowired
    private BinanceProperties binanceProperties;

    @Test
    void shouldUseTestnetUrlsWhenTestnetProfileIsActive() {
        assertThat(exchangeAdapterRepository.hasAdapter("BINANCE")).isTrue();
        assertThat(exchangeAdapterRepository.hasAdapter("MOCK")).isTrue();
        assertThat(binanceProperties.getWsBaseUrl()).contains("testnet.binance.vision");
        assertThat(binanceProperties.getRestBaseUrl()).contains("testnet.binance.vision");
    }

    @SpringBootConfiguration
    @Import({
            InMemoryExchangeAdapterRepository.class,
            InMemoryWebSocketPortRegistry.class,
            MockAdapterConfiguration.class,
            CoinbaseAdapterConfiguration.class,
            BinanceAdapterConfiguration.class,
            ExchangeCircuitBreakerConfiguration.class,
            ExchangeCircuitBreakerProperties.class
    })
    static class TestApplication {

        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        EventPublisherPort eventPublisherPort() {
            return new EventPublisherPort() {
                @Override
                public void publishEvent(Object event) {
                }

                @Override
                public void publishEventSync(Object event) {
                }
            };
        }
    }
}
