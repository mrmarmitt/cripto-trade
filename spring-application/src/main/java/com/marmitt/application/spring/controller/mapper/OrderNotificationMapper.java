package com.marmitt.application.spring.controller.mapper;

import com.marmitt.core.dto.websocket.request.MessageRequest;
import com.marmitt.core.dto.websocket.request.OrderNotificationStreamRequest;
import com.marmitt.core.enums.MessageType;

public class OrderNotificationMapper {

    public static MessageRequest toSubscribeSendMessageRequest(OrderNotificationStreamRequest request) {
        return new MessageRequest(request.exchange(), MessageType.ACCOUNT_UPDATE) {};
    }

    public static MessageRequest toUnsubscribeSendMessageRequest(OrderNotificationStreamRequest request) {
        return new MessageRequest(request.exchange(), MessageType.STREAM_UNSUBSCRIPTION) {};
    }

}
