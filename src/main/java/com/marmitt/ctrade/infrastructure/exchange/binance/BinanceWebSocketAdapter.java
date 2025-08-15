package com.marmitt.ctrade.infrastructure.exchange.binance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marmitt.ctrade.application.service.WebSocketService;
import com.marmitt.ctrade.domain.port.ExchangeWebSocketAdapter;
import com.marmitt.ctrade.infrastructure.config.WebSocketProperties;
import com.marmitt.ctrade.infrastructure.websocket.ReconnectionStrategy;
import com.marmitt.ctrade.infrastructure.websocket.WebSocketCircuitBreaker;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicLong;

@Component
@ConditionalOnProperty(name = "websocket.exchange", havingValue = "BINANCE", matchIfMissing = false)
@Slf4j
public class BinanceWebSocketAdapter implements ExchangeWebSocketAdapter {
    
    private final WebSocketProperties properties;
    private final TaskScheduler taskScheduler;
    private final ReconnectionStrategy reconnectionStrategy;
    private final WebSocketCircuitBreaker circuitBreaker;
    private final ObjectMapper objectMapper;
    private final OkHttpClient okHttpClient;
    
    @Setter
    private WebSocketService webSocketService;
    private WebSocket webSocket;
    private ConnectionStatus status = ConnectionStatus.DISCONNECTED;
    private final Set<String> subscribedPairs = ConcurrentHashMap.newKeySet();
    private boolean orderUpdatesSubscribed = false;
    
    // Connection stats
    private final AtomicLong totalConnections = new AtomicLong(0);
    private final AtomicLong totalReconnections = new AtomicLong(0);
    private final AtomicLong totalMessagesReceived = new AtomicLong(0);
    private final AtomicLong totalErrors = new AtomicLong(0);
    private LocalDateTime lastConnectedAt;
    private LocalDateTime lastMessageAt;
    
    private ScheduledFuture<?> reconnectionTask;
    
    public BinanceWebSocketAdapter(WebSocketProperties properties,
                                 TaskScheduler taskScheduler,
                                 ReconnectionStrategy reconnectionStrategy,
                                 WebSocketCircuitBreaker circuitBreaker,
                                 ObjectMapper objectMapper) {
        this.properties = properties;
        this.taskScheduler = taskScheduler;
        this.reconnectionStrategy = reconnectionStrategy;
        this.circuitBreaker = circuitBreaker;
        this.objectMapper = objectMapper;
        this.okHttpClient = new OkHttpClient.Builder()
                .readTimeout(Duration.ZERO) // No read timeout for WebSocket
                .build();
    }

    @Override
    public String getExchangeName() {
        return "binance";
    }
    
    @Override
    public void connect() {
        if (!circuitBreaker.canConnect()) {
            log.warn("Circuit breaker is OPEN, cannot connect to Binance WebSocket");
            return;
        }
        
        if (status == ConnectionStatus.CONNECTING || status == ConnectionStatus.CONNECTED) {
            log.debug("Already connecting or connected to Binance WebSocket");
            return;
        }
        
        status = ConnectionStatus.CONNECTING;
        log.info("Connecting to Binance WebSocket: {}", properties.getUrl());
        
        Request request = new Request.Builder()
                .url(properties.getUrl())
                .build();
        
        webSocket = okHttpClient.newWebSocket(request, createWebSocketListener());
        totalConnections.incrementAndGet();
    }
    
    @Override
    public void disconnect() {
        status = ConnectionStatus.DISCONNECTED;
        subscribedPairs.clear();
        orderUpdatesSubscribed = false;
        
        if (reconnectionTask != null && !reconnectionTask.isCancelled()) {
            reconnectionTask.cancel(false);
        }
        
        if (webSocket != null) {
            webSocket.close(1000, "Manual disconnect");
            webSocket = null;
        }
        
        log.info("Disconnected from Binance WebSocket");
    }
    
    @Override
    public boolean isConnected() {
        return status == ConnectionStatus.CONNECTED;
    }
    
    @Override
    public void subscribeToPrice(String tradingPair) {
        if (status != ConnectionStatus.CONNECTED) {
            log.warn("Cannot subscribe to {}: WebSocket not connected", tradingPair);
            return;
        }
        
        subscribedPairs.add(tradingPair);
        log.info("Subscribed to price updates for: {}", tradingPair);
        
        // For Binance, we're using a general ticker stream
        // In a real implementation, you'd send subscription messages here
    }
    
    @Override
    public void subscribeToOrderUpdates() {
        if (status != ConnectionStatus.CONNECTED) {
            log.warn("Cannot subscribe to order updates: WebSocket not connected");
            return;
        }
        
        orderUpdatesSubscribed = true;
        log.info("Subscribed to order updates");
        
        // In a real implementation, you'd send subscription messages here
    }
    
    @Override
    public ConnectionStatus getConnectionStatus() {
        return status;
    }
    
    @Override
    public Set<String> getSubscribedPairs() {
        return Set.copyOf(subscribedPairs);
    }
    
    @Override
    public boolean isOrderUpdatesSubscribed() {
        return orderUpdatesSubscribed;
    }
    
    @Override
    public ConnectionStats getConnectionStats() {
        return new ConnectionStats(
            totalConnections.get(),
            totalReconnections.get(),
            totalMessagesReceived.get(),
            totalErrors.get(),
            lastConnectedAt,
            lastMessageAt
        );
    }
    
    @Override
    public void forceReconnect() {
        log.info("Force reconnection requested");
        disconnect();
        scheduleReconnection(Duration.ofSeconds(1));
    }
    
    private void scheduleReconnection(Duration delay) {
        if (reconnectionStrategy.shouldReconnect()) {
            status = ConnectionStatus.RECONNECTING;
            
            reconnectionTask = taskScheduler.schedule(() -> {
                reconnectionStrategy.recordAttempt();
                totalReconnections.incrementAndGet();
                connect();
            }, java.time.Instant.now().plus(delay));
            
            log.info("Reconnection scheduled in {}", delay);
        } else {
            status = ConnectionStatus.FAILED;
            log.error("Max reconnection attempts reached, marking as FAILED");
        }
    }
    
    private BinanceWebSocketListener createWebSocketListener() {
        return new BinanceWebSocketListener(
            objectMapper,
            webSocketService,
            subscribedPairs,
            reconnectionStrategy,
            circuitBreaker,
            totalMessagesReceived,
            totalErrors,
            // Status updater
            newStatus -> this.status = newStatus,
            // Last connected at updater
            time -> this.lastConnectedAt = time,
            // Last message at updater
            time -> this.lastMessageAt = time,
            // Schedule reconnection callback
            () -> scheduleReconnection(reconnectionStrategy.getNextDelay())
        );
    }
}