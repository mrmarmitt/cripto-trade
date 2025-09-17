package com.marmitt.service;

import com.marmitt.controller.dto.WebSocketConnectRequest;
import com.marmitt.core.domain.ConnectionResult;
import com.marmitt.core.dto.configuration.CurrencyPair;
import com.marmitt.core.dto.configuration.WebSocketConnectionParameters;
import com.marmitt.core.dto.websocket.ConnectionResultMapper;
import com.marmitt.core.dto.websocket.WebSocketConnectionManager;
import com.marmitt.core.dto.websocket.WebSocketConnectionResponse;
import com.marmitt.core.ports.inbound.websocket.ConnectWebSocketPort;
import com.marmitt.core.ports.outbound.ExchangeAdapterPort;
import com.marmitt.repository.InMemoryExchangeAdapterRepository;
import com.marmitt.repository.InMemoryWebSocketConnectionRepository;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ExchangeConnectService {

    private final ConnectWebSocketPort connectWebSocket;
    private final InMemoryWebSocketConnectionRepository connectionRepository;
    private final InMemoryExchangeAdapterRepository adapterRepository;

    public ExchangeConnectService(ConnectWebSocketPort connectWebSocket, InMemoryWebSocketConnectionRepository connectionRepository, InMemoryExchangeAdapterRepository adapterRepository) {
        this.connectWebSocket = connectWebSocket;
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
}
