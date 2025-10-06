package com.marmitt.core.ports.inbound.websocket;

import com.marmitt.core.dto.websocket.response.WebSocketStatsResponse;

import java.util.Map;
import java.util.Set;

public interface ConnectStatsWebSocketPort {
    WebSocketStatsResponse getStats(String exchange);
    Map<String, WebSocketStatsResponse> getAllStats();
    boolean hasExchange(String exchange);
    Set<String> getAllExchangeNames();
}
