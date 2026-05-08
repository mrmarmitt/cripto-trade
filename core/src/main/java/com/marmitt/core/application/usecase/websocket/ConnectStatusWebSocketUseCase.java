package com.marmitt.core.application.usecase.websocket;

import com.marmitt.core.dto.connection.ConnectionKey;
import com.marmitt.core.dto.connection.ConnectionResultDto;
import com.marmitt.core.dto.websocket.mapper.ConnectionResultMapper;
import com.marmitt.core.dto.wrapper.WebSocketConnectionManager;
import com.marmitt.core.dto.websocket.response.WebSocketConnectionResponse;
import com.marmitt.core.enums.StreamChannel;
import com.marmitt.core.ports.inbound.websocket.ConnectStatusWebSocketPort;
import com.marmitt.core.ports.outbound.repository.WebSocketConnectionRepositoryPort;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class ConnectStatusWebSocketUseCase implements ConnectStatusWebSocketPort {

    private final WebSocketConnectionRepositoryPort webSocketConnectionRepository;

    public ConnectStatusWebSocketUseCase(WebSocketConnectionRepositoryPort webSocketConnectionRepository) {
        this.webSocketConnectionRepository = webSocketConnectionRepository;
    }

    @Override
    public Map<String, WebSocketConnectionResponse> getStatus(final String exchangeName) {
        Map<String, WebSocketConnectionResponse> result = new LinkedHashMap<>();
        for (StreamChannel channel : StreamChannel.values()) {
            WebSocketConnectionManager manager = webSocketConnectionRepository.getConnection(new ConnectionKey(exchangeName, channel));
            if (manager != null) {
                result.put(channel.name(), ConnectionResultMapper.toResponse(manager.getConnectionResult(), manager.getExchangeName()));
            }
        }
        if (result.isEmpty()) {
            result.put(StreamChannel.MARKET.name(), ConnectionResultMapper.toResponse(
                    ConnectionResultDto.failure(this.getClass().getSimpleName(), "Exchange not found: " + exchangeName),
                    exchangeName));
        }
        return result;
    }

    @Override
    public Map<String, WebSocketConnectionResponse> getAllStatus() {
        return webSocketConnectionRepository.getAllConnections().entrySet().stream()
                .collect(Collectors.toMap(
                        e -> e.getKey().exchangeName() + ":" + e.getKey().channel().name(),
                        e -> ConnectionResultMapper.toResponse(e.getValue().getConnectionResult(), e.getValue().getExchangeName())
                ));
    }

    @Override
    public boolean hasExchange(String exchange) {
        return webSocketConnectionRepository.hasConnection(ConnectionKey.market(exchange));
    }

    @Override
    public Set<String> getAllExchangeNames() {
        return webSocketConnectionRepository.getAllExchangeNames();
    }
}
