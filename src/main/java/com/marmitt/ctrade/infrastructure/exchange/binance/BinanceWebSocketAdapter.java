package com.marmitt.ctrade.infrastructure.exchange.binance;

import com.marmitt.ctrade.infrastructure.config.WebSocketProperties;
import com.marmitt.ctrade.infrastructure.exchange.binance.parser.BinanceStreamParser;
import com.marmitt.ctrade.infrastructure.websocket.AbstractWebSocketAdapter;
import com.marmitt.ctrade.infrastructure.websocket.ReconnectionStrategy;
import com.marmitt.ctrade.infrastructure.websocket.WebSocketCircuitBreaker;
import com.marmitt.ctrade.infrastructure.websocket.WebSocketConnectionHandler;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.WebSocket;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConditionalOnProperty(name = "websocket.exchange", havingValue = "BINANCE", matchIfMissing = false)
@Slf4j
public class BinanceWebSocketAdapter extends AbstractWebSocketAdapter {

    private final WebSocketConnectionHandler connectionHandler;
    // Binance-specific dependencies
    private final BinanceStreamParser streamParser;
    private final OkHttpClient okHttpClient;

    // Binance-specific connection
    private WebSocket webSocket;

    public BinanceWebSocketAdapter(WebSocketProperties properties,
                                   ReconnectionStrategy reconnectionStrategy,
                                   WebSocketCircuitBreaker circuitBreaker,
                                   WebSocketConnectionHandler connectionHandler,
                                   BinanceStreamParser streamParser) {
        super(properties, reconnectionStrategy, circuitBreaker);
        this.connectionHandler = connectionHandler;
        this.streamParser = streamParser;
        this.okHttpClient = new OkHttpClient.Builder()
                .readTimeout(Duration.ZERO) // No read timeout for WebSocket
                .build();
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

        webSocket = okHttpClient.newWebSocket(request, createWebSocketListener());
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
        // For Binance, we're using a general ticker stream
        // In a real implementation, you'd send subscription messages here
    }

    @Override
    protected void doSubscribeToOrderUpdates() {
        // In a real implementation, you'd send subscription messages here
    }


    private BinanceWebSocketListener createWebSocketListener() {
        return new BinanceWebSocketListener(
                connectionHandler,
                streamParser,
                reconnectionStrategy,
                circuitBreaker,
                // Schedule reconnection callback
                () -> scheduleReconnection(reconnectionStrategy.getNextDelay()),
                // Price update callback - publica evento
                this::onPriceUpdate,
                // Order update callback - publica evento
                this::onOrderUpdate
        );
    }
}