package com.marmitt.application.spring.service;

import com.marmitt.application.spring.controller.mapper.MarketDataMapper;
import com.marmitt.core.dto.websocket.request.MarketDataStreamRequest;
import com.marmitt.core.dto.websocket.request.StreamSubscriptionRequest;
import com.marmitt.core.dto.websocket.response.MarketDataStreamResponse;
import com.marmitt.core.ports.inbound.websocket.SendMessageWebSocketPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class MarketDataService {

    private final SendMessageWebSocketPort sendMessageWebSocket;

    public MarketDataService(SendMessageWebSocketPort sendMessageWebSocket) {
        this.sendMessageWebSocket = sendMessageWebSocket;
    }

    public MarketDataStreamResponse subscribe(MarketDataStreamRequest request) {
        StreamSubscriptionRequest sendStreamRequest = MarketDataMapper.toSubscribeSendStreamRequest(request);
        sendMessageWebSocket.execute(sendStreamRequest);
        return MarketDataStreamResponse.successfully("subscription");
    }

    public MarketDataStreamResponse unsubscribe(MarketDataStreamRequest request) {
        StreamSubscriptionRequest sendStreamRequest = MarketDataMapper.toUnsubscribeSendStreamRequest(request);
        sendMessageWebSocket.execute(sendStreamRequest);
        return MarketDataStreamResponse.successfully("unsubscription");
    }
}
