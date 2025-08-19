package com.marmitt.ctrade.infrastructure.exchange.binance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marmitt.ctrade.domain.port.TradingPairProvider;
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
    private final TradingPairProvider tradingPairProvider;
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
                                   TradingPairProvider tradingPairProvider,
                                   ObjectMapper objectMapper) {

        super(eventPublisher,
                connectionManager,
                statsTracker,
                properties);

        this.tradingPairProvider = tradingPairProvider;
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
                            TradingPairProvider tradingPairProvider,
                            OkHttpClient okHttpClient,
                            BinanceWebSocketListener binanceWebSocketListener) {
        super(eventPublisher,
                connectionManager,
                statsTracker,
                properties);

        this.tradingPairProvider = tradingPairProvider;
        this.binanceWebSocketListener = binanceWebSocketListener;
        this.okHttpClient = okHttpClient;
    }

    @Override
    public String getExchangeName() {
        return "BINANCE";
    }

    @Override
    protected void doConnect() {
        String streamUrl = buildStreamUrl();
        log.info("Connecting to Binance WebSocket with URL: {}", streamUrl);
        
        Request request = new Request.Builder()
                .url(streamUrl)
                .build();

        webSocket = okHttpClient.newWebSocket(request, binanceWebSocketListener);
    }
    
    /**
     * Constrói a URL do stream com base nos trading pairs configurados.
     * Usa base URL das propriedades e adiciona os streams dos trading pairs ativos.
     */
    private String buildStreamUrl() {
        String baseUrl = properties.getUrl();
        String streamList = tradingPairProvider.getFormattedStreamList();
        
        if (streamList.isEmpty()) {
            log.warn("No trading pairs configured, using base URL: {}", baseUrl);
            return baseUrl;
        }
        
        // Se a URL base já contém parâmetros de stream, substitui
        // Se não, adiciona como parâmetro streams
        if (baseUrl.contains("?streams=")) {
            String streamUrl = baseUrl.replaceAll("\\?streams=.*$", "?streams=" + streamList);
            log.debug("Replaced streams in URL: {} -> {}", baseUrl, streamUrl);
            return streamUrl;
        } else if (baseUrl.contains("/stream")) {
            String streamUrl = baseUrl + "?streams=" + streamList;
            log.debug("Added streams to URL: {} -> {}", baseUrl, streamUrl);
            return streamUrl;
        } else {
            log.debug("Using base URL unchanged: {}", baseUrl);
            return baseUrl;
        }
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