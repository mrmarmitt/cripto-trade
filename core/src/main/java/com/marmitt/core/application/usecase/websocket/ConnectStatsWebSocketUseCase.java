package com.marmitt.core.application.usecase.websocket;

import com.marmitt.core.dto.connection.ConnectionKey;
import com.marmitt.core.dto.websocket.mapper.ConnectionStatsMapper;
import com.marmitt.core.dto.wrapper.WebSocketConnectionManager;
import com.marmitt.core.dto.websocket.response.WebSocketStatsResponse;
import com.marmitt.core.enums.StreamChannel;
import com.marmitt.core.ports.inbound.websocket.ConnectStatsWebSocketPort;
import com.marmitt.core.ports.outbound.repository.WebSocketConnectionRepositoryPort;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class ConnectStatsWebSocketUseCase implements ConnectStatsWebSocketPort {

    private final WebSocketConnectionRepositoryPort webSocketConnectionRepository;

    public ConnectStatsWebSocketUseCase(WebSocketConnectionRepositoryPort webSocketConnectionRepository) {
        this.webSocketConnectionRepository = webSocketConnectionRepository;
    }

    @Override
    public Map<String, WebSocketStatsResponse> getStats(final String exchangeName) {
        Map<String, WebSocketStatsResponse> result = new LinkedHashMap<>();
        for (StreamChannel channel : StreamChannel.values()) {
            WebSocketConnectionManager manager = webSocketConnectionRepository
                    .getConnection(new ConnectionKey(exchangeName, channel));
            if (manager != null) {
                result.put(channel.name(), ConnectionStatsMapper.toResponse(
                        manager.getConnectionStats(), manager.getExchangeName()));
            }
        }
        if (result.isEmpty()) {
            throw new IllegalArgumentException("Exchange not found: " + exchangeName);
        }
        return result;
    }

    @Override
    public Map<String, WebSocketStatsResponse> getAllStats() {
        return webSocketConnectionRepository.getAllConnections().entrySet().stream()
                .collect(Collectors.toMap(
                        e -> e.getKey().exchangeName() + ":" + e.getKey().channel().name(),
                        e -> ConnectionStatsMapper.toResponse(e.getValue().getConnectionStats(), e.getValue().getExchangeName())
                ));
    }

    @Override
    public boolean hasExchange(String exchange) {
        WebSocketConnectionManager manager = webSocketConnectionRepository.getConnection(ConnectionKey.market(exchange));
        return manager != null && manager.getConnectionStats() != null;
    }

    @Override
    public Set<String> getAllExchangeNames() {
        return webSocketConnectionRepository.getAllExchangeNames();
    }
}
