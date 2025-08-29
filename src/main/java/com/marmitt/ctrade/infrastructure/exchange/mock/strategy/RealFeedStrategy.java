package com.marmitt.ctrade.infrastructure.exchange.mock.strategy;

import com.marmitt.ctrade.domain.dto.OrderUpdateMessage;
import com.marmitt.ctrade.domain.dto.PriceUpdateMessage;
import com.marmitt.ctrade.domain.entity.Order;
import com.marmitt.ctrade.domain.port.TradingPairProvider;
import com.marmitt.ctrade.domain.strategy.StreamProcessingStrategy;
import com.marmitt.ctrade.infrastructure.config.WebSocketProperties;
import com.marmitt.ctrade.infrastructure.websocket.*;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Adaptador WebSocket para conectar diretamente a exchanges reais.
 * Estende AbstractWebSocketAdapter para reutilizar infraestrutura comum.
 */
//@Component
@Slf4j
public class RealFeedStrategy extends AbstractWebSocketAdapter {
    
    private final String exchangeUrl;
    private final String exchangeName;
    private final StreamProcessingStrategy streamProcessingStrategy;
    private final TradingPairProvider tradingPairProvider;
    private final OkHttpClient okHttpClient;
    
    // WebSocket connection
    private WebSocket webSocket;

    public RealFeedStrategy(WebSocketEventPublisher eventPublisher,
                            ConnectionManager connectionManager,
                            ConnectionStatsTracker statsTracker,
                            WebSocketProperties properties,
                            String exchangeUrl,
                            String exchangeName,
                            StreamProcessingStrategy streamProcessingStrategy,
                            TradingPairProvider tradingPairProvider) {
        super(eventPublisher, connectionManager, statsTracker, properties);
        this.exchangeUrl = exchangeUrl;
        this.exchangeName = exchangeName;
        this.streamProcessingStrategy = streamProcessingStrategy;
        this.tradingPairProvider = tradingPairProvider;
        this.okHttpClient = new OkHttpClient();
    }
    
    @Override
    protected void doConnect() {
        log.info("Connecting to real exchange {} at {}", exchangeName, exchangeUrl);
        
        if (streamProcessingStrategy == null) {
            log.error("StreamProcessingStrategy is null for exchange: {}", exchangeName);
            connectionManager.updateStatus(com.marmitt.ctrade.domain.port.ExchangeWebSocketAdapter.ConnectionStatus.DISCONNECTED);
            return;
        }
        
        try {
            String streamUrl = WebSocketUrlBuilder.buildStreamUrl(
                    exchangeUrl,
                    tradingPairProvider.getFormattedStreamList()
            );

            log.info("Connecting to {} WebSocket: {}", exchangeName, streamUrl);
            
            // Create WebSocket request
            Request request = new Request.Builder()
                    .url(streamUrl)
                    .build();
            
            // Create WebSocket listener
            WebSocketListener webSocketListener = new WebSocketListener() {
                @Override
                public void onOpen(WebSocket webSocket, Response response) {
                    log.info("Connected to {} WebSocket successfully. Response code: {}", exchangeName, response.code());
                    connectionManager.updateStatus(ConnectionStatus.CONNECTED);
                }
                
                @Override
                public void onMessage(WebSocket webSocket, String text) {
                    handleMessage(text);
                }
                
                @Override
                public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                    log.error("WebSocket connection failed for {}: {}", exchangeName, t.getMessage(), t);
                    connectionManager.updateStatus(ConnectionStatus.DISCONNECTED);
                    handleError(new RuntimeException(t));
                }
                
                @Override
                public void onClosing(WebSocket webSocket, int code, String reason) {
                    log.info("WebSocket closing for {} - code: {}, reason: {}", exchangeName, code, reason);
                    webSocket.close(1000, null);
                }
                
                @Override
                public void onClosed(WebSocket webSocket, int code, String reason) {
                    log.info("WebSocket closed for {} - code: {}, reason: {}", exchangeName, code, reason);
                    connectionManager.updateStatus(com.marmitt.ctrade.domain.port.ExchangeWebSocketAdapter.ConnectionStatus.DISCONNECTED);
                    handleClose();
                }
            };
            
            // Establish WebSocket connection
            connectionManager.updateStatus(com.marmitt.ctrade.domain.port.ExchangeWebSocketAdapter.ConnectionStatus.CONNECTING);
            webSocket = okHttpClient.newWebSocket(request, webSocketListener);
            
        } catch (Exception e) {
            log.error("Failed to connect to real exchange {}: {}", exchangeName, e.getMessage(), e);
            connectionManager.updateStatus(com.marmitt.ctrade.domain.port.ExchangeWebSocketAdapter.ConnectionStatus.DISCONNECTED);
            handleError(e);
        }
    }
    
    @Override
    protected void doDisconnect() {
        log.info("Disconnecting from real exchange {}", exchangeName);
        if (webSocket != null) {
            webSocket.close(1000, "Manual disconnect");
            webSocket = null;
        }
        connectionManager.updateStatus(com.marmitt.ctrade.domain.port.ExchangeWebSocketAdapter.ConnectionStatus.DISCONNECTED);
    }
    
    @Override
    protected void doSubscribeToPrice(String tradingPair) {
        // Real feed uses stream parameters, no dynamic subscription needed
        log.debug("Price subscription for {} already handled via stream parameters", tradingPair);
    }
    
    @Override
    protected void doSubscribeToOrderUpdates() {
        // Real feed for order updates would require authentication
        log.debug("Order update subscription not implemented for real feed");
    }
    
    @Override
    public String getExchangeName() {
        return exchangeName;
    }
    
    /**
     * Processa mensagens recebidas do WebSocket real.
     */
    private void handleMessage(String message) {
        try {
            log.debug("Received WebSocket message from {}: {}", exchangeName, message);
            
            if (streamProcessingStrategy != null) {
                // Process price updates
                Optional<PriceUpdateMessage> priceUpdate = streamProcessingStrategy.processPriceUpdate(message);
                priceUpdate.ifPresent(this::onPriceUpdate);
                
                // Process order updates if applicable
                Optional<OrderUpdateMessage> orderUpdate = streamProcessingStrategy.processOrderUpdate(message);
                orderUpdate.ifPresent(this::onOrderUpdate);
            } else {
                log.warn("No StreamProcessingStrategy available to process message");
            }
            
        } catch (Exception e) {
            log.error("Error processing WebSocket message from {}: {}", exchangeName, e.getMessage(), e);
        }
    }
    
    /**
     * Manipula erros de conexão WebSocket.
     */
    private void handleError(Exception error) {
        log.error("WebSocket error for exchange {}: {}", exchangeName, error.getMessage(), error);
        statsTracker.recordError();
    }
    
    /**
     * Manipula fechamento de conexão WebSocket.
     */
    private void handleClose() {
        log.info("WebSocket connection closed for exchange {}", exchangeName);
    }
}