package com.marmitt.core.application.usecase.websocket;

import com.marmitt.core.domain.ConnectionResult;
import com.marmitt.core.dto.configuration.WebSocketConnectionParameters;
import com.marmitt.core.dto.websocket.ConnectionResultMapper;
import com.marmitt.core.dto.websocket.WebSocketConnectionManager;
import com.marmitt.core.dto.websocket.WebSocketConnectionResponse;
import com.marmitt.core.ports.inbound.websocket.ConnectWebSocketPort;
import com.marmitt.core.ports.outbound.ExchangeAdapterPort;
import com.marmitt.core.ports.outbound.repository.ExchangeAdapterRepositoryPort;
import com.marmitt.core.ports.outbound.repository.WebSocketConnectionRepositoryPort;

public class ConnectWebSocketUseCase implements ConnectWebSocketPort {

    private final WebSocketConnectionRepositoryPort connectionRepository;
    private final ExchangeAdapterRepositoryPort adapterRepository;

    public ConnectWebSocketUseCase(WebSocketConnectionRepositoryPort connectionRepository, ExchangeAdapterRepositoryPort adapterRepository) {
        this.connectionRepository = connectionRepository;
        this.adapterRepository = adapterRepository;
    }

    @Override
    public WebSocketConnectionResponse execute(WebSocketConnectionParameters parameters, String exchangeName) {

        connectionRepository.registerConnection(exchangeName);
        WebSocketConnectionManager manager = connectionRepository.getConnection(exchangeName);
        ExchangeAdapterPort adapter = adapterRepository.getAdapter(exchangeName);

        manager.setConnectionResult(ConnectionResult.connecting());
        String connectionUrl = adapter.getUrlBuilder().buildConnectionUrl(parameters);
        adapter.getWebSocketPort().connect(connectionUrl, exchangeName, manager.getConnectionId());


        return ConnectionResultMapper.toResponse(manager.getConnectionResult(), exchangeName);
    }

}