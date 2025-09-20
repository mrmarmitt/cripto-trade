package com.marmitt.core.application.usecase.websocket;

import com.marmitt.core.dto.websocket.ConnectionStatsMapper;
import com.marmitt.core.dto.websocket.WebSocketConnectionManager;
import com.marmitt.core.dto.websocket.WebSocketStatsResponse;
import com.marmitt.core.ports.inbound.websocket.ConnectStatsWebSocketPort;
import com.marmitt.core.ports.outbound.repository.WebSocketConnectionRepositoryPort;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class ConnectStatsWebSocketUseCase implements ConnectStatsWebSocketPort {

    private final WebSocketConnectionRepositoryPort webSocketConnectionRepository;

    public ConnectStatsWebSocketUseCase(WebSocketConnectionRepositoryPort webSocketConnectionRepository) {
        this.webSocketConnectionRepository = webSocketConnectionRepository;
    }

    @Override
    public WebSocketStatsResponse getStats(String exchange) {
        WebSocketConnectionManager manager = webSocketConnectionRepository.getConnection(exchange);
        if (manager == null) {
            throw new IllegalArgumentException("Exchange not found: " + exchange);
        }

        return ConnectionStatsMapper.toResponse(
                manager.getConnectionStats(),
                manager.getExchangeName()
        );
    }

    @Override
    public Map<String, WebSocketStatsResponse> getAllStats() {
        return webSocketConnectionRepository.getAllConnections().entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> {
                            String exchangeName = entry.getKey();
                            WebSocketConnectionManager manager = entry.getValue();
                            return ConnectionStatsMapper.toResponse(
                                    manager.getConnectionStats(),
                                    exchangeName
                            );
                        }
                ));
    }

    @Override
    public boolean hasExchange(String exchange) {
        WebSocketConnectionManager manager = webSocketConnectionRepository.getConnection(exchange);
        return manager != null && manager.getConnectionStats() != null;
    }

    @Override
    public Set<String> getAllExchangeNames() {
        return webSocketConnectionRepository.getAllExchangeNames();
    }
}
