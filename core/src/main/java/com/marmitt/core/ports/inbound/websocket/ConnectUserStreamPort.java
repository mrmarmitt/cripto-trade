package com.marmitt.core.ports.inbound.websocket;

import com.marmitt.core.dto.websocket.response.WebSocketConnectionResponse;

import java.util.Optional;

public interface ConnectUserStreamPort {

    Optional<WebSocketConnectionResponse> execute(String exchangeName);

}
