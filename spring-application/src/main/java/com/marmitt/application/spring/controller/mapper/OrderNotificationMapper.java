package com.marmitt.application.spring.controller.mapper;

import com.marmitt.application.spring.controller.dto.order.OrderNotificationStreamRequest;
import com.marmitt.core.dto.websocket.request.SendMessageRequest;
import com.marmitt.core.enums.MessageType;

public class OrderNotificationMapper {

    public static SendMessageRequest toSubscribeSendMessageRequest(OrderNotificationStreamRequest request) {
        return new SendMessageRequest(request.exchange(), MessageType.ACCOUNT_UPDATE) {};
    }

    public static SendMessageRequest toUnsubscribeSendMessageRequest(OrderNotificationStreamRequest request) {
        return new SendMessageRequest(request.exchange(), MessageType.STREAM_UNSUBSCRIPTION) {};
    }

}