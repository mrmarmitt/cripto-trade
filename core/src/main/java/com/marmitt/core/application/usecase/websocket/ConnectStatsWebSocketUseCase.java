package com.marmitt.core.application.usecase.websocket;

import com.marmitt.core.dto.websocket.mapper.ConnectionStatsMapper;
import com.marmitt.core.dto.wrapper.WebSocketConnectionManager;
import com.marmitt.core.dto.websocket.response.WebSocketStatsResponse;
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
    public WebSocketStatsResponse getStats(final String exchangeName) {
        WebSocketConnectionManager manager = webSocketConnectionRepository.getConnection(exchangeName);
        if (manager == null) {
            throw new IllegalArgumentException("Exchange not found: " + exchangeName);
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
