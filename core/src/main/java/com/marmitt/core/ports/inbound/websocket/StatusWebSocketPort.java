package com.marmitt.core.ports.inbound.websocket;

import com.marmitt.core.dto.websocket.WebSocketConnectionResponse;
import com.marmitt.core.ports.outbound.WebSocketPort;

public interface StatusWebSocketPort {
    WebSocketConnectionResponse execute(WebSocketPort webSocketPort);
}
