package com.marmitt.core.application.usecase;

import com.marmitt.core.domain.ConnectionResult;
import com.marmitt.core.dto.websocket.ConnectionResultMapper;
import com.marmitt.core.dto.websocket.WebSocketConnectionResponse;
import com.marmitt.core.ports.inbound.websocket.StatusWebSocketPort;
import com.marmitt.core.ports.outbound.WebSocketPort;

public class StatusWebSocketUseCase implements StatusWebSocketPort {

    @Override
    public WebSocketConnectionResponse execute(WebSocketPort webSocketPort) {
        ConnectionResult result = webSocketPort.getConnectionResult();
        return ConnectionResultMapper.toResponse(result);
    }
}
