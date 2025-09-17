package com.marmitt.service;

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
import com.marmitt.core.ports.outbound.ExchangeAdapterPort;
import com.marmitt.repository.InMemoryExchangeAdapterRepository;
import com.marmitt.repository.InMemoryWebSocketConnectionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
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
    private final InMemoryWebSocketConnectionRepository connectionRepository;
    private final InMemoryExchangeAdapterRepository adapterRepository;

    public WebSocketExampleService(
            ConnectWebSocketPort connectWebSocket,
            DisconnectWebSocketPort disconnectWebSocket,
            InMemoryWebSocketConnectionRepository connectionRepository,
            InMemoryExchangeAdapterRepository adapterRepository) {

        this.connectWebSocket = connectWebSocket;
        this.disconnectWebSocket = disconnectWebSocket;
        this.connectionRepository = connectionRepository;
        this.adapterRepository = adapterRepository;
    }

    public CompletableFuture<WebSocketConnectionResponse> connect(WebSocketConnectRequest request) {
        String exchange = request.exchange();

        try {
            // Obtém o adapter para a exchange
            ExchangeAdapterPort adapter = adapterRepository.getAdapter(exchange);
            if (adapter == null) {
                return CompletableFuture.completedFuture(
                        ConnectionResultMapper.toResponse(
                                ConnectionResult.failure("Unsupported exchange: " + exchange),
                                exchange
                        )
                );
            }

            // Obtém o status atual da conexão e passa para o use case
            connectionRepository.registerConnection(exchange);
            WebSocketConnectionManager manager = connectionRepository.getConnection(exchange);

            // Adiciona contexto no MDC para logs de conexão
            MDC.put("exchangeName", exchange);

            return connectToExchange(request, manager, adapter);
        } finally {
            MDC.clear();
        }
    }

    private CompletableFuture<WebSocketConnectionResponse> connectToExchange(
            WebSocketConnectRequest request,
            WebSocketConnectionManager manager,
            ExchangeAdapterPort adapter) {
        
        WebSocketConnectionParameters connectionParams = buildWebSocketConnectionParameters(request);
        ConnectionResult currentStatus = manager.getConnectionResult();
        String exchangeName = adapter.getExchangeName();

        return connectWebSocket.execute(
                connectionParams, 
                manager, 
                adapter.getUrlBuilder(), 
                adapter.getWebSocketPort(), 
                adapter.getMessageProcessor())
                .thenApply(response -> {
                    if (response.isSuccess() && !currentStatus.isConnected()) {
                        manager.onConnected();

                        // Lógica específica para Coinbase (subscribe message)
                        if ("COINBASE".equalsIgnoreCase(exchangeName) && 
                            adapter.getMessageProcessor() instanceof CoinbaseMessageProcessor coinbaseListener) {
                            try {
                                for (com.marmitt.controller.dto.CurrencyPair pair : request.symbols()) {
                                    String coinbaseSymbol = pair.baseCurrency() + "-" + pair.quoteCurrency();
                                    String subscribeMessage = coinbaseListener.createSubscribeMessage(coinbaseSymbol);
                                    adapter.getWebSocketPort().sendMessage(subscribeMessage);
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
                    log.error("Failed to connect to {} WebSocket", exchangeName, throwable);
                    manager.onFailure("Connection failed", throwable);
                    return ConnectionResultMapper.toResponse(
                            ConnectionResult.failure("Connection failed: " + throwable.getMessage()),
                            manager.getExchangeName()
                    );
                })
                .whenComplete((result, throwable) -> {
                    if (throwable == null && result.isSuccess()) {
                        MDC.put("connectionId", manager.getConnectionId().toString());
                        log.info("Successfully connected to {} WebSocket for symbols: {}", exchangeName, request.symbols());
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
        
        try {
            ExchangeAdapterPort adapter = adapterRepository.getAdapter(exchange);
            if (adapter == null) {
                return CompletableFuture.completedFuture(
                        ConnectionResultMapper.toResponse(
                                ConnectionResult.failure("Unsupported exchange: " + exchange),
                                exchange
                        )
                );
            }

            WebSocketConnectionManager manager = connectionRepository.getConnection(exchange);
            // Adiciona contexto no MDC para logs de desconexão
            MDC.put("exchangeName", manager.getExchangeName());
            MDC.put("connectionId", manager.getConnectionId().toString());

            manager.startDisconnection();

            return disconnectWebSocket.execute(adapter.getWebSocketPort())
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
        } finally {
            MDC.clear();
        }
    }

    public WebSocketConnectionResponse getStatus(String exchange) {
        WebSocketConnectionManager manager = connectionRepository.getConnection(exchange);
        ConnectionResult result = manager.getConnectionResult();
        return ConnectionResultMapper.toResponse(result, manager.getExchangeName());
    }

    public WebSocketStatsResponse getStats(String exchange) {
        WebSocketConnectionManager manager = connectionRepository.getConnection(exchange);
        return ConnectionStatsMapper.toResponse(manager.getConnectionStats(), manager.getExchangeName());
    }

    public Map<String, WebSocketConnectionResponse> getAllStatus() {
        return connectionRepository.getAllConnections().entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> ConnectionResultMapper.toResponse(entry.getValue().getConnectionResult(), entry.getKey())
                ));
    }

    public Map<String, WebSocketStatsResponse> getAllStats() {
        return connectionRepository.getAllConnections().entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> ConnectionStatsMapper.toResponse(entry.getValue().getConnectionStats(), entry.getKey())
                ));
    }
}