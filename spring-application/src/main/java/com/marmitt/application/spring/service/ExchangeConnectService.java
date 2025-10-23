package com.marmitt.application.spring.service;

import com.marmitt.application.spring.controller.dto.WebSocketConnectRequest;
import com.marmitt.core.dto.common.CurrencyPair;
import com.marmitt.core.dto.websocket.request.StreamSubscriptionRequest;
import com.marmitt.core.dto.websocket.response.WebSocketConnectionResponse;
import com.marmitt.core.enums.StreamAction;
import com.marmitt.core.ports.inbound.websocket.ConnectWebSocketPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
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

        StreamSubscriptionRequest connectionParams = buildStreamSubscriptionRequest(request);

        return connectWebSocket.execute(exchange, connectionParams);
    }

    private StreamSubscriptionRequest buildStreamSubscriptionRequest(WebSocketConnectRequest request) {
        List<CurrencyPair> coreCurrencyPairs = request.symbols().stream()
                .map(pair -> new CurrencyPair(pair.baseCurrency(), pair.quoteCurrency(), pair.streamType()))
                .collect(Collectors.toList());

        return new StreamSubscriptionRequest(request.exchange(),coreCurrencyPairs, StreamAction.SUBSCRIBE);
    }
}
