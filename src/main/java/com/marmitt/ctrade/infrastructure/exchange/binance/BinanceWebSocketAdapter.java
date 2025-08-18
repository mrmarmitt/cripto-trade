package com.marmitt.ctrade.infrastructure.exchange.binance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marmitt.ctrade.infrastructure.config.WebSocketProperties;
import com.marmitt.ctrade.infrastructure.websocket.*;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.WebSocket;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConditionalOnProperty(name = "websocket.exchange", havingValue = "BINANCE", matchIfMissing = false)
@Slf4j
public class BinanceWebSocketAdapter extends AbstractWebSocketAdapter {

    // Binance-specific dependencies
    private final OkHttpClient okHttpClient;

    private final BinanceWebSocketListener binanceWebSocketListener;
    // Binance-specific connection
    private WebSocket webSocket;

    /**
     * Construtor principal para uso em produção.
     * Cria automaticamente a BinanceStreamProcessingStrategy.
     */
    @Autowired
    public BinanceWebSocketAdapter(WebSocketProperties properties,
                                   WebSocketConnectionHandler connectionHandler,
                                   ConnectionManager connectionManager,
                                   ConnectionStatsTracker statsTracker,
                                   WebSocketEventPublisher eventPublisher,
                                   ObjectMapper objectMapper) {

        super(eventPublisher,
                connectionManager,
                statsTracker,
                properties);

        this.binanceWebSocketListener = createWebSocketListener(connectionHandler, objectMapper);

        this.okHttpClient = new OkHttpClient.Builder()
                .readTimeout(Duration.ZERO) // No read timeout for WebSocket
                .build();
    }

    /**
     * Construtor para testes unitários.
     * Permite injetar uma StreamProcessingStrategy mockada.
     */
    BinanceWebSocketAdapter(WebSocketProperties properties,
                            ConnectionManager connectionManager,
                            ConnectionStatsTracker statsTracker,
                            WebSocketEventPublisher eventPublisher,
                            OkHttpClient okHttpClient,
                            BinanceWebSocketListener binanceWebSocketListener) {
        super(eventPublisher,
                connectionManager,
                statsTracker,
                properties);

        this.binanceWebSocketListener = binanceWebSocketListener;
        this.okHttpClient = okHttpClient;
    }

    @Override
    public String getExchangeName() {
        return "BINANCE";
    }

    @Override
    protected void doConnect() {
        Request request = new Request.Builder()
                .url(properties.getUrl())
                .build();

        webSocket = okHttpClient.newWebSocket(request, binanceWebSocketListener);
    }

    @Override
    protected void doDisconnect() {
        if (webSocket != null) {
            webSocket.close(1000, "Manual disconnect");
            webSocket = null;
        }
    }


    @Override
    protected void doSubscribeToPrice(String tradingPair) {
        // TODO: Implementar subscrição dinâmica para pares específicos
        // Enviar mensagem WebSocket: {"method": "SUBSCRIBE", "params": ["btcusdt@ticker"], "id": 1}
        // For Binance, we're using a general ticker stream
        // In a real implementation, you'd send subscription messages here
    }

    @Override
    protected void doSubscribeToOrderUpdates() {
        // TODO: Implementar subscrição para atualizações de ordem do usuário
        // Requer autenticação via listenKey do Binance User Data Stream
        // Enviar mensagem WebSocket para user data stream endpoint
        // In a real implementation, you'd send subscription messages here
    }


    private BinanceWebSocketListener createWebSocketListener(WebSocketConnectionHandler connectionHandler, ObjectMapper objectMapper) {
        return new BinanceWebSocketListener(
                connectionHandler,
                objectMapper,
                // Schedule reconnection callback
                this::scheduleReconnection,
                // Price update callback - publica evento
                this::onPriceUpdate,
                // Order update callback - publica evento
                this::onOrderUpdate
        );
    }
}