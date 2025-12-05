package com.marmitt.core.dto.websocket.mapper;

import com.marmitt.core.dto.connection.ConnectionResultDto;
import com.marmitt.core.dto.websocket.response.WebSocketConnectionResponse;

public class ConnectionResultMapper {
    
    public static WebSocketConnectionResponse toResponse(ConnectionResultDto connectionResult, String exchangeName) {
        return new WebSocketConnectionResponse(
                connectionResult.status(),
                connectionResult.message(),
                connectionResult.timestamp(),
                exchangeName,
                connectionResult.connectionId() != null ? connectionResult.connectionId().toString() : null,
                connectionResult.metadata(),
                connectionResult.isConnected(),
                connectionResult.isSuccess(),
                connectionResult.isInProgress(),
                connectionResult.isFailed(),
                connectionResult.getConnectionDuration()
        );
    }
}