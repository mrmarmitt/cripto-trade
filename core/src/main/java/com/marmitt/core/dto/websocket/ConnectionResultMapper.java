package com.marmitt.core.dto.websocket;

import com.marmitt.core.domain.ConnectionResult;

public class ConnectionResultMapper {
    
    public static WebSocketConnectionResponse toResponse(ConnectionResult connectionResult, String exchangeName) {
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