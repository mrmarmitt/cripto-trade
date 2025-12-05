package com.marmitt.application.spring.service;

import com.marmitt.core.dto.websocket.response.WebSocketConnectionResponse;
import com.marmitt.core.ports.inbound.websocket.DisconnectWebSocketPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class ExchangeDisconnectService {

    private final DisconnectWebSocketPort disconnectWebSocket;

    public ExchangeDisconnectService(DisconnectWebSocketPort disconnectWebSocket) {
        this.disconnectWebSocket = disconnectWebSocket;
    }

    public WebSocketConnectionResponse disconnect(String exchangeName) {
        return disconnectWebSocket.execute(exchangeName);
    }
}
