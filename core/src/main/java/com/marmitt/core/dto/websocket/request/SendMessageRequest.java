package com.marmitt.core.dto.websocket.request;

import com.marmitt.core.enums.MessageType;
import lombok.Getter;

@Getter
public abstract class SendMessageRequest {
    
    private final String exchangeName;
    private final MessageType messageType;
    
    protected SendMessageRequest(String exchangeName, MessageType messageType) {
        this.exchangeName = exchangeName;
        this.messageType = messageType;
    }

}