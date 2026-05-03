package com.marmitt.core.ports.inbound.websocket;

import com.marmitt.core.dto.websocket.response.WebSocketConnectionResponse;

public interface ConnectUserStreamPort {

    WebSocketConnectionResponse execute(String exchangeName);

}
