package com.marmitt.core.application.usecase.websocket;

import com.marmitt.core.dto.connection.ConnectionResultDto;
import com.marmitt.core.dto.websocket.mapper.ConnectionResultMapper;
import com.marmitt.core.dto.websocket.request.StreamSubscriptionRequest;
import com.marmitt.core.dto.websocket.response.WebSocketConnectionResponse;
import com.marmitt.core.dto.wrapper.WebSocketConnectionManager;
import com.marmitt.core.enums.ConnectionStatus;
import com.marmitt.core.ports.inbound.websocket.ConnectWebSocketPort;
import com.marmitt.core.ports.outbound.exchange.streaming.ExchangeStreamingPort;
import com.marmitt.core.ports.outbound.repository.ExchangeAdapterRepositoryPort;
import com.marmitt.core.ports.outbound.repository.WebSocketConnectionRepositoryPort;

import java.util.Optional;

public class ConnectWebSocketUseCase implements ConnectWebSocketPort {

    private final WebSocketConnectionRepositoryPort connectionRepository;
    private final ExchangeAdapterRepositoryPort adapterRepository;

    public ConnectWebSocketUseCase(WebSocketConnectionRepositoryPort connectionRepository,
                                   ExchangeAdapterRepositoryPort adapterRepository) {
        this.connectionRepository = connectionRepository;
        this.adapterRepository = adapterRepository;
    }

    @Override
    public WebSocketConnectionResponse execute(final String exchangeName, final StreamSubscriptionRequest parameters) {
        connectionRepository.registerConnection(exchangeName);
        WebSocketConnectionManager manager = connectionRepository.getConnection(exchangeName);

        Optional<ExchangeStreamingPort> streamingOptional = adapterRepository.findStreamingByName(exchangeName);
        if (streamingOptional.isEmpty()) {
            return ConnectionResultMapper.toResponse(
                    ConnectionResultDto.failure(this.getClass().getSimpleName(), "Exchange does not exist"),
                    exchangeName);
        }

        ConnectionStatus currentStatus = manager.getConnectionResult().status();
        boolean isReconnect = currentStatus == ConnectionStatus.ERROR
                           || currentStatus == ConnectionStatus.CLOSED;

        if (isReconnect) {
            manager.setConnectionResult(ConnectionResultDto.reconnecting(1, 1));
        } else {
            manager.setConnectionResult(ConnectionResultDto.connecting());
        }

        manager.addRequestToHistory(parameters);

        ExchangeStreamingPort streaming = streamingOptional.get();
        String connectionUrl = streaming.getUrlBuilder().buildConnectionUrl(parameters);
        streaming.getWebSocketPort().connect(connectionUrl, exchangeName, manager.getConnectionId());

        return ConnectionResultMapper.toResponse(manager.getConnectionResult(), exchangeName);
    }
}

