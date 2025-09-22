package com.marmitt.core.ports.inbound.websocket;

import com.marmitt.core.dto.websocket.request.SendMessageRequest;

public interface SendMessageWebSocketPort {
    void execute(SendMessageRequest request);
}
