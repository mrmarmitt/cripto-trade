package com.marmitt.core.ports.inbound.websocket;

import com.marmitt.core.dto.websocket.request.MessageRequest;

public interface SendMessageWebSocketPort {
    void execute(MessageRequest request);
}
