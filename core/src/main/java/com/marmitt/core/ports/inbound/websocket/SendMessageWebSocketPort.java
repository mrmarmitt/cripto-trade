package com.marmitt.core.ports.inbound.websocket;

import com.marmitt.core.dto.websocket.request.MessageRequest;
import com.marmitt.core.dto.websocket.response.SendWebSocketResponse;

public interface SendMessageWebSocketPort {
    SendWebSocketResponse execute(MessageRequest request);
}
