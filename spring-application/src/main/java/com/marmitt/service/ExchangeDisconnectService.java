package com.marmitt.service;

import com.marmitt.core.domain.ConnectionResult;
import com.marmitt.core.dto.websocket.mapper.ConnectionResultMapper;
import com.marmitt.core.dto.websocket.response.WebSocketConnectionResponse;
import com.marmitt.core.ports.inbound.websocket.DisconnectWebSocketPort;
import com.marmitt.core.ports.outbound.exchange.adapter.ExchangeAdapterPort;
import com.marmitt.repository.InMemoryExchangeAdapterRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class ExchangeDisconnectService {

    private final DisconnectWebSocketPort disconnectWebSocket;
    private final InMemoryExchangeAdapterRepository adapterRepository;

    public ExchangeDisconnectService(DisconnectWebSocketPort disconnectWebSocket, InMemoryExchangeAdapterRepository adapterRepository) {
        this.disconnectWebSocket = disconnectWebSocket;
        this.adapterRepository = adapterRepository;
    }

    public WebSocketConnectionResponse disconnect(String exchangeName) {

        ExchangeAdapterPort adapter = adapterRepository.getAdapter(exchangeName);
        if (adapter == null) {
            //TODO: lançar uma exception no lugar dessa resposta.
            ConnectionResultMapper.toResponse(ConnectionResult.failure("Exchange does not exist"), exchangeName);
        }

        return disconnectWebSocket.execute(exchangeName);
    }
}
