package com.marmitt.core.application.usecase.websocket;

import com.marmitt.core.domain.ConnectionResult;
import com.marmitt.core.dto.websocket.mapper.ConnectionResultMapper;
import com.marmitt.core.dto.wrapper.WebSocketConnectionManager;
import com.marmitt.core.dto.websocket.response.WebSocketConnectionResponse;
import com.marmitt.core.ports.inbound.websocket.DisconnectWebSocketPort;
import com.marmitt.core.ports.outbound.exchange.adapter.ExchangeAdapterPort;
import com.marmitt.core.ports.outbound.repository.ExchangeAdapterRepositoryPort;
import com.marmitt.core.ports.outbound.repository.WebSocketConnectionRepositoryPort;

import java.util.UUID;

public class DisconnectWebSocketUseCase implements DisconnectWebSocketPort {

    private final WebSocketConnectionRepositoryPort connectionRepository;
    private final ExchangeAdapterRepositoryPort adapterRepository;

    public DisconnectWebSocketUseCase(WebSocketConnectionRepositoryPort connectionRepository, ExchangeAdapterRepositoryPort adapterRepository) {
        this.connectionRepository = connectionRepository;
        this.adapterRepository = adapterRepository;
    }

    @Override
    public WebSocketConnectionResponse execute(final String exchangeName) {
        WebSocketConnectionManager manager = connectionRepository.getConnection(exchangeName);
        ExchangeAdapterPort adapter = adapterRepository.getAdapter(exchangeName);
        UUID connectionId = manager.getConnectionId();

        manager.setConnectionResult(ConnectionResult.disconnecting("Manual disconnection requested", connectionId));

        adapter.getWebSocketPort().disconnect(exchangeName, connectionId);

        return ConnectionResultMapper.toResponse(manager.getConnectionResult(), exchangeName);
    }
}