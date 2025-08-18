package com.marmitt.ctrade.infrastructure.exchange.binance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marmitt.ctrade.domain.dto.PriceUpdateMessage;
import com.marmitt.ctrade.domain.port.TradingPairProvider;
import com.marmitt.ctrade.infrastructure.config.WebSocketProperties;
import com.marmitt.ctrade.infrastructure.exchange.binance.strategy.BinanceStreamProcessingStrategy;
import com.marmitt.ctrade.infrastructure.websocket.ConnectionManager;
import com.marmitt.ctrade.infrastructure.websocket.ConnectionStatsTracker;
import com.marmitt.ctrade.infrastructure.websocket.WebSocketConnectionHandler;
import com.marmitt.ctrade.infrastructure.websocket.WebSocketEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Teste de integração para BinanceWebSocketAdapter.
 * Usa o construtor de produção com implementações reais para testar o fluxo completo.
 */
@ExtendWith(MockitoExtension.class)
class BinanceWebSocketAdapterIntegrationTest {

    @Mock
    private WebSocketProperties properties;
    
    @Mock
    private WebSocketConnectionHandler connectionHandler;
    
    @Mock
    private ConnectionManager connectionManager;
    
    @Mock
    private ConnectionStatsTracker statsTracker;
    
    @Mock
    private WebSocketEventPublisher eventPublisher;
    
    @Mock
    private TradingPairProvider tradingPairProvider;

    private ObjectMapper objectMapper;
    private BinanceWebSocketAdapter adapter;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        
        // Usa o construtor de produção (com implementações reais)
        adapter = new BinanceWebSocketAdapter(
                properties,
                connectionHandler,
                connectionManager,
                statsTracker,
                eventPublisher,
                tradingPairProvider,
                objectMapper  // ObjectMapper real → cria BinanceStreamProcessingStrategy real + listeners reais
        );
    }

    @Test
    void shouldCreateAdapterWithRealImplementations() {
        // When
        String exchangeName = adapter.getExchangeName();
        
        // Then
        assertThat(exchangeName).isEqualTo("BINANCE");
    }

    @Test
    void shouldCreateRealStrategySuccessfully() {
        // Given
        BinanceStreamProcessingStrategy realStrategy = new BinanceStreamProcessingStrategy(objectMapper);
        
        // When
        String exchangeName = realStrategy.getExchangeName();
        
        // Then - Verifica que a strategy real foi criada corretamente
        assertThat(exchangeName).isEqualTo("BINANCE");
    }

    @Test
    void shouldHandleInvalidMessageGracefully() {
        // Given - Strategy real
        BinanceStreamProcessingStrategy realStrategy = new BinanceStreamProcessingStrategy(objectMapper);
        String invalidMessage = "invalid json message";
        
        // When
        Optional<PriceUpdateMessage> result = realStrategy.processPriceUpdate(invalidMessage);
        
        // Then - Deve retornar empty sem lançar exceção
        assertThat(result).isEmpty();
    }
}