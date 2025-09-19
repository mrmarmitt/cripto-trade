package com.marmitt.core.ports.inbound.websocket;

import com.marmitt.core.dto.websocket.WebSocketConnectionResponse;

public interface DisconnectWebSocketPort {

    WebSocketConnectionResponse execute(String exchangeName);

}