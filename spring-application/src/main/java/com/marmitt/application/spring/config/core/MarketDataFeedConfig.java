package com.marmitt.application.spring.config.core;

import com.marmitt.application.spring.bootstrap.OperationalStateProperties;
import com.marmitt.core.application.usecase.websocket.EnsureMarketDataFeedUseCase;
import com.marmitt.core.ports.inbound.websocket.ConnectMarketStreamPort;
import com.marmitt.core.ports.inbound.websocket.ConnectUserStreamPort;
import com.marmitt.core.ports.inbound.websocket.EnsureMarketDataFeedPort;
import com.marmitt.core.ports.inbound.websocket.SendMessageWebSocketPort;
import com.marmitt.core.ports.outbound.marketdata.LatestPricePort;
import com.marmitt.core.ports.outbound.repository.ExchangeAdapterRepositoryPort;
import com.marmitt.core.ports.outbound.repository.StrategyRunnerRepositoryPort;
import com.marmitt.core.ports.outbound.repository.WebSocketConnectionRepositoryPort;
import com.marmitt.core.ports.outbound.runner.RunnerActivationObserverPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Duration;

/**
 * Coordenador de demanda de market data (T56). O limiar de "sem tick" é o mesmo do estado consolidado
 * ({@code ctrade.observability.market-data-stale-ms}): o que a T55 acusa, a T56 corrige.
 */
@Configuration
public class MarketDataFeedConfig {

    @Bean
    public EnsureMarketDataFeedPort ensureMarketDataFeed(StrategyRunnerRepositoryPort strategyRunnerRepository,
                                                         ExchangeAdapterRepositoryPort exchangeAdapterRepository,
                                                         WebSocketConnectionRepositoryPort webSocketConnections,
                                                         LatestPricePort latestPrices,
                                                         ConnectMarketStreamPort connectMarketStream,
                                                         ConnectUserStreamPort connectUserStream,
                                                         SendMessageWebSocketPort sendMessageWebSocket,
                                                         MarketDataFeedProperties properties,
                                                         OperationalStateProperties operationalStateProperties) {
        return new EnsureMarketDataFeedUseCase(strategyRunnerRepository, exchangeAdapterRepository,
                webSocketConnections, latestPrices, connectMarketStream, connectUserStream, sendMessageWebSocket,
                properties::streamTypeFor, Duration.ofMillis(operationalStateProperties.getMarketDataStaleMs()),
                Clock.systemUTC());
    }

    /**
     * Runner que volta a operar dispara o coordenador na hora, sem esperar o ciclo. Desligado o
     * automático, ninguém observa.
     */
    @Bean
    public RunnerActivationObserverPort runnerActivationObserver(EnsureMarketDataFeedPort ensureMarketDataFeed,
                                                                 MarketDataFeedProperties properties) {
        return properties.isEnabled() ? runnerId -> ensureMarketDataFeed.ensure() : RunnerActivationObserverPort.NONE;
    }
}
