package com.marmitt.application.spring.service;

import com.marmitt.application.spring.controller.dto.order.OrderNotificationStreamRequest;
import com.marmitt.application.spring.controller.dto.order.OrderNotificationStreamResponse;
import com.marmitt.application.spring.controller.mapper.OrderNotificationMapper;
import com.marmitt.core.dto.websocket.request.SendMessageRequest;
import com.marmitt.core.ports.inbound.websocket.SendMessageWebSocketPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class OrderNotificationService {

    private final SendMessageWebSocketPort sendMessageWebSocket;

    public OrderNotificationService(SendMessageWebSocketPort sendMessageWebSocket) {
        this.sendMessageWebSocket = sendMessageWebSocket;
    }

    public OrderNotificationStreamResponse subscribe(OrderNotificationStreamRequest request) {
        SendMessageRequest sendMessageRequest = OrderNotificationMapper.toSubscribeSendMessageRequest(request);
        sendMessageWebSocket.execute(sendMessageRequest);
        return OrderNotificationStreamResponse.successfully("subscription");
    }

    public OrderNotificationStreamResponse unsubscribe( OrderNotificationStreamRequest request) {
        SendMessageRequest sendMessageRequest = OrderNotificationMapper.toUnsubscribeSendMessageRequest(request);
        sendMessageWebSocket.execute(sendMessageRequest);
        return OrderNotificationStreamResponse.successfully("unsubscription");
    }
}
