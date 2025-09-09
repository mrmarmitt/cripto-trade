package com.marmitt.service;

import com.marmitt.adapter.OkHttp3WebSocketAdapter;
import com.marmitt.coinbase.processor.CoinbaseMessageProcessor;
import com.marmitt.controller.dto.WebSocketConnectRequest;
import com.marmitt.core.domain.ConnectionResult;
import com.marmitt.core.enums.ConnectionStatus;
import com.marmitt.core.dto.configuration.CurrencyPair;
import com.marmitt.core.dto.configuration.WebSocketConnectionParameters;
import com.marmitt.core.dto.websocket.ConnectionResultMapper;
import com.marmitt.core.dto.websocket.WebSocketConnectionManager;
import com.marmitt.core.dto.websocket.WebSocketConnectionResponse;
import com.marmitt.core.dto.websocket.WebSocketStatsResponse;
import com.marmitt.core.dto.websocket.ConnectionStatsMapper;
import com.marmitt.core.ports.inbound.websocket.ConnectWebSocketPort;
import com.marmitt.core.ports.inbound.websocket.DisconnectWebSocketPort;
import com.marmitt.core.ports.outbound.ExchangeUrlBuilderPort;
import com.marmitt.core.ports.outbound.websocket.MessageProcessorPort;
import com.marmitt.core.ports.outbound.websocket.WebSocketPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
public class WebSocketExampleService {

    private static final Logger log = LoggerFactory.getLogger(WebSocketExampleService.class);


    private final ConnectWebSocketPort connectWebSocket;
    private final DisconnectWebSocketPort disconnectWebSocket;
    private final WebSocketConnectionRegistry connectionRegistry;

    private final WebSocketPort binanceWebSocketPort;
    private final MessageProcessorPort binanceWebSocketListener;
    private final ExchangeUrlBuilderPort binanceUrlBuilder;

    private final WebSocketPort coinbaseWebSocketPort;
    private final MessageProcessorPort coinbaseWebSocketListener;
    private final ExchangeUrlBuilderPort coinbaseUrlBuilder;

    public WebSocketExampleService(
            ConnectWebSocketPort connectWebSocket,
            DisconnectWebSocketPort disconnectWebSocket,
            WebSocketConnectionRegistry connectionRegistry,
            ApplicationEventPublisher eventPublisher,
            MessageProcessorPort binanceWebSocketListener,
            ExchangeUrlBuilderPort binanceUrlBuilder,
            MessageProcessorPort coinbaseWebSocketListener,
            ExchangeUrlBuilderPort coinbaseUrlBuilder) {


        this.connectWebSocket = connectWebSocket;
        this.disconnectWebSocket = disconnectWebSocket;
        this.connectionRegistry = connectionRegistry;

        this.binanceWebSocketPort = new OkHttp3WebSocketAdapter(eventPublisher);
        this.binanceWebSocketListener = binanceWebSocketListener;
        this.binanceUrlBuilder = binanceUrlBuilder;

        this.coinbaseWebSocketPort = new OkHttp3WebSocketAdapter(eventPublisher);
        this.coinbaseWebSocketListener = coinbaseWebSocketListener;
        this.coinbaseUrlBuilder = coinbaseUrlBuilder;
    }


    public CompletableFuture<WebSocketConnectionResponse> connect(WebSocketConnectRequest request) {
        String exchange = request.exchange();

        // Obtém o status atual da conexão e passa para o use case
        connectionRegistry.createConnection(exchange);
        WebSocketConnectionManager manager = connectionRegistry.getConnection(exchange);

        // Seleciona exchange e conecta (toda lógica de validação está no use case)
        if ("BINANCE".equalsIgnoreCase(exchange)) {
            return connectToBinance(request, manager);
        }

        if ("COINBASE".equalsIgnoreCase(exchange)) {
            return connectToCoinbase(request, manager);
        }

        return CompletableFuture.completedFuture(
                ConnectionResultMapper.toResponse(
                        ConnectionResult.failure("Unsupported exchange: " + exchange),
                        exchange
                )
        );
    }

    private CompletableFuture<WebSocketConnectionResponse> connectToBinance(WebSocketConnectRequest request, WebSocketConnectionManager manager) {
        WebSocketConnectionParameters connectionParams = buildWebSocketConnectionParameters(request);
        ConnectionResult currentStatus = manager.getConnectionResult();

        return connectWebSocket.execute(connectionParams, manager, binanceUrlBuilder, binanceWebSocketPort, binanceWebSocketListener)
                .thenApply(response -> {
                    if (response.isSuccess() && !currentStatus.isConnected()) {
                        manager.onConnected();
                    }
                    return response;
                })
                .exceptionally(throwable -> {
                    log.error("Failed to connect to Binance WebSocket", throwable);
                    manager.onFailure("Connection failed", throwable);
                    return ConnectionResultMapper.toResponse(
                            ConnectionResult.failure("Connection failed: " + throwable.getMessage()),
                            manager.getExchangeName()
                    );
                })
                .whenComplete((result, throwable) -> {
                    if (throwable == null && result.isSuccess()) {
                        log.info("Successfully connected to Binance WebSocket for symbols: {}", request.symbols());
                    }
                });
    }

    private CompletableFuture<WebSocketConnectionResponse> connectToCoinbase(WebSocketConnectRequest request, WebSocketConnectionManager manager) {
        WebSocketConnectionParameters connectionParams = buildWebSocketConnectionParameters(request);
        ConnectionResult currentStatus = manager.getConnectionResult();

        return connectWebSocket.execute(connectionParams, manager, coinbaseUrlBuilder, coinbaseWebSocketPort, coinbaseWebSocketListener)
                .thenApply(response -> {
                    if (response.isSuccess() && !currentStatus.isConnected()) {
                        manager.onConnected();

                        // Envia mensagem de subscribe para Coinbase após conexão estabelecida
                        if (coinbaseWebSocketListener instanceof CoinbaseMessageProcessor coinbaseListener) {
                            try {
                                // Para cada currency pair, envia mensagem de subscribe
                                for (com.marmitt.controller.dto.CurrencyPair pair : request.symbols()) {
                                    String coinbaseSymbol = pair.baseCurrency() + "-" + pair.quoteCurrency();
                                    String subscribeMessage = coinbaseListener.createSubscribeMessage(coinbaseSymbol);
                                    coinbaseWebSocketPort.sendMessage(subscribeMessage);
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
                    return ConnectionResultMapper.toResponse(
                            ConnectionResult.failure("Connection failed: " + throwable.getMessage()),
                            manager.getExchangeName()
                    );
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
        WebSocketConnectionManager manager = connectionRegistry.getConnection(exchange);
        manager.startDisconnection();

        WebSocketPort webSocketPort = "BINANCE".equalsIgnoreCase(exchange) ? binanceWebSocketPort : coinbaseWebSocketPort;

        return disconnectWebSocket.execute(webSocketPort)
                .thenApply(response -> {
                    manager.onClosed(1000, "Manual disconnect");
                    // Retorna o estado atualizado do manager, não a response original
                    return ConnectionResultMapper.toResponse(manager.getConnectionResult(), manager.getExchangeName());
                })
                .exceptionally(throwable -> {
                    log.error("Failed to disconnect from {} WebSocket", exchange, throwable);
                    manager.onFailure("Disconnect failed", throwable);
                    return ConnectionResultMapper.toResponse(manager.getConnectionResult(), manager.getExchangeName());
                })
                .whenComplete((result, throwable) -> {
                    if (throwable == null) {
                        if (result.status() == ConnectionStatus.CLOSED || result.status() == ConnectionStatus.DISCONNECTED) {
                            log.info("Successfully disconnected from {} WebSocket", exchange);
                        } else if (!result.isSuccess()) {
                            log.warn("Disconnect completed with status: {} for {}", result.status(), exchange);
                        } else {
                            log.debug("Disconnect operation completed with success status: {} for {}", result.status(), exchange);
                        }
                    }
                });
    }

    public WebSocketConnectionResponse getStatus(String exchange) {
        WebSocketConnectionManager manager = connectionRegistry.getConnection(exchange);
        ConnectionResult result = manager.getConnectionResult();
        return ConnectionResultMapper.toResponse(result, manager.getExchangeName());
    }

    public WebSocketStatsResponse getStats(String exchange) {
        WebSocketConnectionManager manager = connectionRegistry.getConnection(exchange);
        return ConnectionStatsMapper.toResponse(manager.getConnectionStats(), manager.getExchangeName());
    }

    public Map<String, WebSocketConnectionResponse> getAllStatus() {
        return connectionRegistry.getAllConnections().entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> ConnectionResultMapper.toResponse(entry.getValue().getConnectionResult(), entry.getKey())
                ));
    }

    public Map<String, WebSocketStatsResponse> getAllStats() {
        return connectionRegistry.getAllConnections().entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> ConnectionStatsMapper.toResponse(entry.getValue().getConnectionStats(), entry.getKey())
                ));
    }
}