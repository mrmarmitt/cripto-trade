package com.marmitt.service;

import com.marmitt.controller.dto.WebSocketConnectRequest;
import com.marmitt.core.domain.ConnectionResult;
import com.marmitt.core.dto.configuration.CurrencyPair;
import com.marmitt.core.dto.websocket.ConnectionResultMapper;
import com.marmitt.core.dto.websocket.WebSocketConnectionResponse;
import com.marmitt.core.dto.websocket.WebSocketConnectionManager;
import com.marmitt.coinbase.listener.CoinbaseWebSocketListener;
import com.marmitt.core.dto.configuration.WebSocketConnectionParameters;
import com.marmitt.core.enums.StreamType;
import com.marmitt.core.ports.inbound.websocket.ConnectWebSocketPort;
import com.marmitt.core.ports.inbound.websocket.DisconnectWebSocketPort;
import com.marmitt.core.ports.inbound.websocket.StatusWebSocketPort;
import com.marmitt.core.ports.outbound.ExchangeUrlBuilderPort;
import com.marmitt.core.ports.outbound.WebSocketListenerPort;
import com.marmitt.core.ports.outbound.WebSocketPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
public class WebSocketExampleService {

    private static final Logger log = LoggerFactory.getLogger(WebSocketExampleService.class);

    private final WebSocketPort webSocketPort;
    private final ConnectWebSocketPort connectWebSocket;
    private final DisconnectWebSocketPort disconnectWebSocket;
    private final StatusWebSocketPort statusWebSocket;
    private final WebSocketConnectionRegistry connectionRegistry;

    private final WebSocketListenerPort binanceWebSocketListener;
    private final ExchangeUrlBuilderPort binanceUrlBuilder;

    private final WebSocketListenerPort coinbaseWebSocketListener;
    private final ExchangeUrlBuilderPort coinbaseUrlBuilder;

    public WebSocketExampleService(
            WebSocketPort webSocketPort,
            ConnectWebSocketPort connectWebSocket,
            DisconnectWebSocketPort disconnectWebSocket,
            StatusWebSocketPort statusWebSocket,
            WebSocketConnectionRegistry connectionRegistry,
            WebSocketListenerPort binanceWebSocketListener,
            ExchangeUrlBuilderPort binanceUrlBuilder,
            WebSocketListenerPort coinbaseWebSocketListener,
            ExchangeUrlBuilderPort coinbaseUrlBuilder) {

        this.webSocketPort = webSocketPort;
        this.connectWebSocket = connectWebSocket;
        this.disconnectWebSocket = disconnectWebSocket;
        this.statusWebSocket = statusWebSocket;
        this.connectionRegistry = connectionRegistry;
        this.binanceWebSocketListener = binanceWebSocketListener;
        this.binanceUrlBuilder = binanceUrlBuilder;
        this.coinbaseWebSocketListener = coinbaseWebSocketListener;
        this.coinbaseUrlBuilder = coinbaseUrlBuilder;
    }


    public CompletableFuture<WebSocketConnectionResponse> connect(WebSocketConnectRequest request) {
        String exchange = request.exchange();
        
        // Obtém o status atual da conexão e passa para o use case
        WebSocketConnectionManager manager = connectionRegistry.getOrCreateConnection(exchange);
        ConnectionResult currentStatus = manager.getConnectionResult();

        // Inicia processo de conexão se necessário
        if (!currentStatus.isConnected() && !currentStatus.isInProgress()) {
            manager.startConnection();
        }

        // Seleciona exchange e conecta (toda lógica de validação está no use case)
        if ("BINANCE".equalsIgnoreCase(exchange)) {
            return connectToBinance(request, manager, currentStatus);
        } else if ("COINBASE".equalsIgnoreCase(exchange)) {
            return connectToCoinbase(request, manager, currentStatus);
        } else {
            return CompletableFuture.completedFuture(
                ConnectionResultMapper.toResponse(
                    ConnectionResult.failure("Unsupported exchange: " + exchange)
                )
            );
        }
    }
    
    private CompletableFuture<WebSocketConnectionResponse> connectToBinance(WebSocketConnectRequest request, WebSocketConnectionManager manager, ConnectionResult currentStatus) {
        WebSocketConnectionParameters connectionParams = buildWebSocketConnectionParameters(request);

        return connectWebSocket.execute(connectionParams, currentStatus, binanceUrlBuilder, webSocketPort, binanceWebSocketListener)
                .thenApply(response -> {
                    if (response.isSuccess() && !currentStatus.isConnected()) {
                        manager.onConnected();
                    }
                    return response;
                })
                .exceptionally(throwable -> {
                    log.error("Failed to connect to Binance WebSocket", throwable);
                    manager.onFailure("Connection failed", throwable);
                    return ConnectionResultMapper.toResponse(ConnectionResult.failure("Connection failed: " + throwable.getMessage()));
                })
                .whenComplete((result, throwable) -> {
                    if (throwable == null && result.isSuccess()) {
                        log.info("Successfully connected to Binance WebSocket for symbols: {}", request.symbols());
                    }
                });
    }
    
    private CompletableFuture<WebSocketConnectionResponse> connectToCoinbase(WebSocketConnectRequest request, WebSocketConnectionManager manager, ConnectionResult currentStatus) {
        WebSocketConnectionParameters connectionParams = buildWebSocketConnectionParameters(request);

        return connectWebSocket.execute(connectionParams, currentStatus, coinbaseUrlBuilder, webSocketPort, coinbaseWebSocketListener)
                .thenApply(response -> {
                    if (response.isSuccess() && !currentStatus.isConnected()) {
                        manager.onConnected();
                        
                        // Envia mensagem de subscribe para Coinbase após conexão estabelecida
                        if (coinbaseWebSocketListener instanceof CoinbaseWebSocketListener coinbaseListener) {
                            try {
                                // Para cada currency pair, envia mensagem de subscribe
                                for (com.marmitt.controller.dto.CurrencyPair pair : request.symbols()) {
                                    String coinbaseSymbol = pair.baseCurrency() + "-" + pair.quoteCurrency();
                                    String subscribeMessage = coinbaseListener.createSubscribeMessage(coinbaseSymbol);
                                    webSocketPort.sendMessage(subscribeMessage);
                                    log.info("Sent subscribe message to Coinbase: {}", subscribeMessage);
                                }
                            } catch (Exception e) {
                                log.error("Failed to send subscribe message to Coinbase", e);
                            }
                        }
                    }
                    
                    return response;
                })
                .exceptionally(throwable -> {
                    log.error("Failed to connect to Coinbase WebSocket", throwable);
                    manager.onFailure("Connection failed", throwable);
                    return ConnectionResultMapper.toResponse(ConnectionResult.failure("Connection failed: " + throwable.getMessage()));
                })
                .whenComplete((result, throwable) -> {
                    if (throwable == null && result.isSuccess()) {
                        log.info("Successfully connected to Coinbase WebSocket for symbols: {}", request.symbols());
                    }
                });
    }

    private WebSocketConnectionParameters buildWebSocketConnectionParameters(WebSocketConnectRequest request) {
        List<CurrencyPair> coreCurrencyPairs = request.symbols().stream()
                .map(pair -> new CurrencyPair(pair.baseCurrency(), pair.quoteCurrency()))
                .collect(Collectors.toList());

         return WebSocketConnectionParameters.of(
                List.of(request.streamType()),
                coreCurrencyPairs
        );
    }

    public CompletableFuture<WebSocketConnectionResponse> disconnect(String exchange) {
        WebSocketConnectionManager manager = connectionRegistry.getOrCreateConnection(exchange);
        manager.startDisconnection();
        
        return disconnectWebSocket.execute(webSocketPort)
                .thenApply(response -> {
                    manager.onClosed(1000, "Manual disconnect");
                    return response;
                })
                .exceptionally(throwable -> {
                    log.error("Failed to disconnect from {} WebSocket", exchange, throwable);
                    manager.onFailure("Disconnect failed", throwable);
                    return ConnectionResultMapper.toResponse(ConnectionResult.failure("Disconnect failed: " + throwable.getMessage()));
                })
                .whenComplete((result, throwable) -> {
                    if (throwable == null && result.isSuccess()) {
                        log.info("Successfully disconnected from {} WebSocket", exchange);
                    }
                });
    }

    public WebSocketConnectionResponse getStatus(String exchange) {
        WebSocketConnectionManager manager = connectionRegistry.getOrCreateConnection(exchange);
        ConnectionResult result = manager.getConnectionResult();
        return ConnectionResultMapper.toResponse(result);
    }
}