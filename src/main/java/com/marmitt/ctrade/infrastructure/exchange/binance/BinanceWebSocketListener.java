package com.marmitt.ctrade.infrastructure.exchange.binance;

import com.marmitt.ctrade.domain.dto.OrderUpdateMessage;
import com.marmitt.ctrade.domain.dto.PriceUpdateMessage;
import com.marmitt.ctrade.domain.port.ExchangeWebSocketAdapter;
import com.marmitt.ctrade.infrastructure.exchange.binance.parser.BinanceStreamParser;
import com.marmitt.ctrade.infrastructure.websocket.ReconnectionStrategy;
import com.marmitt.ctrade.infrastructure.websocket.WebSocketCircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import org.jetbrains.annotations.NotNull;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

@RequiredArgsConstructor
@Slf4j
public class BinanceWebSocketListener extends WebSocketListener {
    
    private final BinanceStreamParser streamParser;
    private final Set<String> subscribedPairs;
    private final ReconnectionStrategy reconnectionStrategy;
    private final WebSocketCircuitBreaker circuitBreaker;
    
    // Stats tracking
    private final AtomicLong totalMessagesReceived;
    private final AtomicLong totalErrors;
    
    // Status and timing callbacks
    private final Consumer<ExchangeWebSocketAdapter.ConnectionStatus> statusUpdater;
    private final Consumer<LocalDateTime> lastConnectedAtUpdater;
    private final Consumer<LocalDateTime> lastMessageAtUpdater;
    private final Runnable scheduleReconnectionCallback;
    
    // Message processing callbacks
    private final Consumer<PriceUpdateMessage> onPriceUpdate;
    private final Consumer<OrderUpdateMessage> onOrderUpdate;
    
    @Override
    public void onOpen(@NotNull WebSocket webSocket, @NotNull Response response) {
        statusUpdater.accept(ExchangeWebSocketAdapter.ConnectionStatus.CONNECTED);
        lastConnectedAtUpdater.accept(LocalDateTime.now());
        reconnectionStrategy.reset();
        circuitBreaker.recordSuccess();
        
        log.info("Connected to Binance WebSocket successfully");
    }
    
    @Override
    public void onMessage(@NotNull WebSocket webSocket, @NotNull String text) {
        try {
            lastMessageAtUpdater.accept(LocalDateTime.now());
            totalMessagesReceived.incrementAndGet();
            
            log.debug("Received message from Binance: {}", text.substring(0, Math.min(100, text.length())));
            
            // Delegar processamento para o parser strategy com callbacks
            Optional<PriceUpdateMessage> priceUpdateMessage = streamParser.parseMessage(text);
            priceUpdateMessage.ifPresent(onPriceUpdate);

        } catch (Exception e) {
            totalErrors.incrementAndGet();
            log.error("Error processing Binance WebSocket message: {}", e.getMessage(), e);
        }
    }
    
    @Override
    public void onClosing(@NotNull WebSocket webSocket, int code, @NotNull String reason) {
        log.warn("Binance WebSocket connection closing: {} - {}", code, reason);
    }
    
    @Override
    public void onClosed(@NotNull WebSocket webSocket, int code, @NotNull String reason) {
        statusUpdater.accept(ExchangeWebSocketAdapter.ConnectionStatus.DISCONNECTED);
        log.warn("Binance WebSocket connection closed: {} - {}", code, reason);
        
        // Schedule reconnection if not manually disconnected
        if (code != 1000) { // 1000 = normal closure
            circuitBreaker.recordFailure();
            scheduleReconnectionCallback.run();
        }
    }
    
    @Override
    public void onFailure(@NotNull WebSocket webSocket, @NotNull Throwable t, Response response) {
        statusUpdater.accept(ExchangeWebSocketAdapter.ConnectionStatus.FAILED);
        totalErrors.incrementAndGet();
        circuitBreaker.recordFailure();
        
        log.error("Binance WebSocket connection failed: {}", t.getMessage(), t);
        
        scheduleReconnectionCallback.run();
    }
}