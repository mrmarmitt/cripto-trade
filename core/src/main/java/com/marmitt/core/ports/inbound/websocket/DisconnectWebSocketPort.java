package com.marmitt.core.ports.inbound.websocket;

import com.marmitt.core.dto.websocket.response.WebSocketConnectionResponse;

public interface DisconnectWebSocketPort {

    WebSocketConnectionResponse execute(String exchangeName);

}