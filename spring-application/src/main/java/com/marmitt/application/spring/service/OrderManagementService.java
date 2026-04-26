package com.marmitt.application.spring.service;

import com.marmitt.application.spring.controller.mapper.OrderManagementMapper;
import com.marmitt.core.dto.websocket.request.SendCancelOrderRequest;
import com.marmitt.core.dto.websocket.request.OrderCancelRequest;
import com.marmitt.core.dto.websocket.request.OrderCreateRequest;
import com.marmitt.core.dto.websocket.request.SendOrderRequest;
import com.marmitt.core.dto.websocket.response.OrderManagementResponse;
import com.marmitt.core.ports.inbound.websocket.SendMessageWebSocketPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class OrderManagementService {

    private final SendMessageWebSocketPort sendMessageWebSocket;

    public OrderManagementService(SendMessageWebSocketPort sendMessageWebSocket) {
        this.sendMessageWebSocket = sendMessageWebSocket;
    }

    public OrderManagementResponse subscribe(OrderCreateRequest request) {
        SendOrderRequest sendOrderRequest = OrderManagementMapper.toSendOrderRequest(request);
        sendMessageWebSocket.execute(sendOrderRequest);
        return OrderManagementResponse.successfully("creation");
    }

    public OrderManagementResponse unsubscribe(OrderCancelRequest request) {
        SendCancelOrderRequest sendCancelOrderRequest = OrderManagementMapper.toSendCancelOrderRequest(request);
        sendMessageWebSocket.execute(sendCancelOrderRequest);
        return OrderManagementResponse.successfully("cancellation");
    }
}
