package com.marmitt.core.ports.inbound.websocket;

import com.marmitt.core.dto.configuration.WebSocketConnectionParameters;
import com.marmitt.core.dto.websocket.WebSocketConnectionResponse;

public interface ConnectWebSocketPort {

    WebSocketConnectionResponse execute(
            WebSocketConnectionParameters parameters,
            String exchangeName);

}