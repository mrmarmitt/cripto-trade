package com.marmitt.application.spring.bootstrap;

import com.marmitt.core.domain.runner.StrategyRunner;
import com.marmitt.core.dto.connection.ConnectionKey;
import com.marmitt.core.dto.runner.request.CreateRunnerRequest;
import com.marmitt.core.enums.ConnectionStatus;
import com.marmitt.core.ports.outbound.marketdata.LatestPricePort;
import com.marmitt.core.ports.outbound.repository.WebSocketConnectionRepositoryPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * T56 — sem nenhuma chamada a /api/websocket, o coordenador de demanda abre a conexao do MOCK e assina o
 * simbolo do runner ativo; o feed simulado passa a alimentar o runner.
 */
class MarketDataAutoFeedIntegrationTest extends ScenarioStrategyE2ETestSupport {

    private static final UUID SMA_STRATEGY_ID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");

    @Autowired
    private LatestPricePort latestPrices;

    @Autowired
    private WebSocketConnectionRepositoryPort connections;

    @DynamicPropertySource
    static void autoFeed(DynamicPropertyRegistry registry) {
        registry.add("ctrade.market-data.auto-feed.enabled", () -> "true");
        registry.add("ctrade.market-data.auto-feed.initial-delay-ms", () -> "200");
        registry.add("ctrade.market-data.auto-feed.interval-ms", () -> "500");
    }

    @Test
    void activeRunnerReceivesTicksWithoutAnyManualConnection() {
        UUID runnerId = createActiveRunnerOnMockMarketData();

        awaitCondition(() -> connections.hasConnection(ConnectionKey.market(MOCK_EXCHANGE))
                        && connections.getConnection(ConnectionKey.market(MOCK_EXCHANGE)).getConnectionResult().status()
                        == ConnectionStatus.CONNECTED,
                WAIT_TIMEOUT, "conexao de market data do MOCK aberta pelo coordenador", runnerId);
        awaitCondition(() -> latestPrices.findUpdatedAt(SYMBOL).isPresent(), WAIT_TIMEOUT,
                "tick do simbolo do runner chegando", runnerId);
        awaitCondition(() -> signalEvaluatedCount(runnerId, "HOLD") >= 1.0, WAIT_TIMEOUT,
                "o runner avaliou um tick do feed", runnerId);
    }

    private UUID createActiveRunnerOnMockMarketData() {
        UUID runnerId = createRunnerPort.execute(CreateRunnerRequest.builder()
                .portfolioId(createPortfolio())
                .strategyId(SMA_STRATEGY_ID)
                .symbol(SYMBOL)
                .exchangeName(MOCK_EXCHANGE)
                .allowedMarketDataSources(Set.of(MOCK_EXCHANGE))
                .build()).runnerId();
        assertNotNull(runnerId);
        StrategyRunner runner = strategyRunnerRepository.findById(runnerId).orElseThrow();
        runner.startInitializing();
        runner.activate();
        strategyRunnerRepository.save(runner);
        return runnerId;
    }
}
