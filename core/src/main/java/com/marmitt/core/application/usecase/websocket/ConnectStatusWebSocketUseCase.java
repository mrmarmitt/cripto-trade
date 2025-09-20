package com.marmitt.core.application.usecase.websocket;

import com.marmitt.core.domain.ConnectionResult;
import com.marmitt.core.dto.websocket.ConnectionResultMapper;
import com.marmitt.core.dto.websocket.WebSocketConnectionManager;
import com.marmitt.core.dto.websocket.WebSocketConnectionResponse;
import com.marmitt.core.ports.inbound.websocket.ConnectStatusWebSocketPort;
import com.marmitt.core.ports.outbound.repository.WebSocketConnectionRepositoryPort;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class ConnectStatusWebSocketUseCase implements ConnectStatusWebSocketPort {

    private final WebSocketConnectionRepositoryPort webSocketConnectionRepository;

    public ConnectStatusWebSocketUseCase(WebSocketConnectionRepositoryPort webSocketConnectionRepository) {
        this.webSocketConnectionRepository = webSocketConnectionRepository;
    }

    @Override
    public WebSocketConnectionResponse getStatus(String exchange) {
        WebSocketConnectionManager manager = webSocketConnectionRepository.getConnection(exchange);
        if (manager == null) {
            return ConnectionResultMapper.toResponse(
                    ConnectionResult.failure("Exchange not found: " + exchange),
                    exchange
            );
        }

        ConnectionResult result = manager.getConnectionResult();
        return ConnectionResultMapper.toResponse(result, manager.getExchangeName());
    }

    @Override
    public Map<String, WebSocketConnectionResponse> getAllStatus() {
        return webSocketConnectionRepository.getAllConnections().entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> {
                            String exchangeName = entry.getKey();
                            WebSocketConnectionManager manager = entry.getValue();
                            ConnectionResult result = manager.getConnectionResult();
                            return ConnectionResultMapper.toResponse(result, exchangeName);
                        }
                ));
    }

    @Override
    public boolean hasExchange(String exchange) {
        return webSocketConnectionRepository.hasConnection(exchange);
    }

    @Override
    public Set<String> getAllExchangeNames() {
        return webSocketConnectionRepository.getAllExchangeNames();
    }
}
