package com.marmitt.core.ports.inbound.websocket;

import com.marmitt.core.dto.websocket.request.WebSocketConnectionParametersRequest;
import com.marmitt.core.dto.websocket.response.WebSocketConnectionResponse;

public interface ConnectWebSocketPort {

    WebSocketConnectionResponse execute(
            String exchangeName,
            WebSocketConnectionParametersRequest parameters);

}