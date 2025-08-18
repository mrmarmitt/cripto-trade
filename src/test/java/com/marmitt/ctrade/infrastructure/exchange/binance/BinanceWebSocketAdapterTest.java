package com.marmitt.ctrade.infrastructure.exchange.binance;

import com.marmitt.ctrade.infrastructure.config.WebSocketProperties;
import com.marmitt.ctrade.infrastructure.websocket.ConnectionManager;
import com.marmitt.ctrade.infrastructure.websocket.ConnectionStatsTracker;
import com.marmitt.ctrade.infrastructure.websocket.WebSocketEventPublisher;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Teste unitário para BinanceWebSocketAdapter.
 * Usa construtor para testes injetando todos os mocks diretamente.
 */
@ExtendWith(MockitoExtension.class)
class BinanceWebSocketAdapterTest {

    @Mock
    private WebSocketProperties properties;
    
    @Mock
    private ConnectionManager connectionManager;
    
    @Mock
    private ConnectionStatsTracker statsTracker;
    
    @Mock
    private WebSocketEventPublisher eventPublisher;
    
    @Mock
    private OkHttpClient okHttpClient;
    
    @Mock
    private BinanceWebSocketListener binanceWebSocketListener;

    private BinanceWebSocketAdapter adapter;

    @BeforeEach
    void setUp() {
        // Usa o construtor para testes (package-private) - injeta todas as dependências mockadas
        adapter = new BinanceWebSocketAdapter(
                properties,
                connectionManager,
                statsTracker,
                eventPublisher,
                okHttpClient,
                binanceWebSocketListener
        );
    }

    @Test
    void shouldReturnCorrectExchangeName() {
        // When
        String exchangeName = adapter.getExchangeName();
        
        // Then
        assertThat(exchangeName).isEqualTo("BINANCE");
    }

    @Test
    void shouldInitializeWithMockedDependencies() {
        // Then - Verifica que o adapter foi criado corretamente com mocks
        assertThat(adapter.getExchangeName()).isEqualTo("BINANCE");
        assertThat(adapter.isConnected()).isFalse(); // connectionManager está mockado
    }

    @Test
    void shouldDelegateConnectionStatusToConnectionManager() {
        // Given
        when(connectionManager.isConnected()).thenReturn(true);
        
        // When
        boolean isConnected = adapter.isConnected();
        
        // Then
        assertThat(isConnected).isTrue();
    }
}