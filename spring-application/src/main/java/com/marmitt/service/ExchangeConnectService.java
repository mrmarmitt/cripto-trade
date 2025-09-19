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

    public ExchangeConnectService(ConnectWebSocketPort connectWebSocket) {
        this.connectWebSocket = connectWebSocket;
    }

    public WebSocketConnectionResponse connect(WebSocketConnectRequest request) {
        String exchange = request.exchange();

        WebSocketConnectionParameters connectionParams = buildWebSocketConnectionParameters(request);

        return connectWebSocket.execute(connectionParams, exchange);
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
