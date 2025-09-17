package com.marmitt.core.ports.inbound.websocket;

import com.marmitt.core.dto.websocket.WebSocketConnectionResponse;

import java.util.Map;
import java.util.Set;

public interface ConnectStatusWebSocketPort {
    WebSocketConnectionResponse getStatus(String exchange);
    Map<String,WebSocketConnectionResponse> getAllStatus();
    boolean hasExchange(String exchange);
    Set<String> getAllExchangeNames();
}
