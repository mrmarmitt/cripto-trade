package com.marmitt.application.spring.service;

import com.marmitt.core.dto.common.CurrencyPair;
import com.marmitt.core.dto.websocket.request.WebSocketConnectRequest;
import com.marmitt.core.dto.websocket.request.StreamSubscriptionRequest;
import com.marmitt.core.dto.websocket.response.WebSocketConnectionResponse;
import com.marmitt.core.enums.StreamAction;
import com.marmitt.core.ports.inbound.websocket.ConnectMarketStreamPort;
import com.marmitt.core.ports.inbound.websocket.ConnectUserStreamPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class ExchangeConnectService {

    private final ConnectMarketStreamPort connectMarketStream;
    private final ConnectUserStreamPort connectUserStream;

    public ExchangeConnectService(ConnectMarketStreamPort connectMarketStream,
                                  ConnectUserStreamPort connectUserStream) {
        this.connectMarketStream = connectMarketStream;
        this.connectUserStream = connectUserStream;
    }

    public Map<String, WebSocketConnectionResponse> connect(WebSocketConnectRequest request) {
        String exchange = request.exchange();
        StreamSubscriptionRequest connectionParams = buildStreamSubscriptionRequest(request);

        WebSocketConnectionResponse marketResponse = connectMarketStream.execute(exchange, connectionParams);
        WebSocketConnectionResponse userStreamResponse = connectUserStream.execute(exchange);

        Map<String, WebSocketConnectionResponse> result = new LinkedHashMap<>();
        result.put("market", marketResponse);
        result.put("userStream", userStreamResponse);
        return result;
    }

    private StreamSubscriptionRequest buildStreamSubscriptionRequest(WebSocketConnectRequest request) {
        List<CurrencyPair> coreCurrencyPairs = request.symbols().stream()
                .map(pair -> new CurrencyPair(pair.baseCurrency(), pair.quoteCurrency(), pair.streamType()))
                .toList();
        return new StreamSubscriptionRequest(request.exchange(), coreCurrencyPairs, StreamAction.SUBSCRIBE);
    }
}
