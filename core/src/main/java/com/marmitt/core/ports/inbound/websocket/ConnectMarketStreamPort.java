package com.marmitt.core.ports.inbound.websocket;

import com.marmitt.core.dto.websocket.request.StreamSubscriptionRequest;
import com.marmitt.core.dto.websocket.response.WebSocketConnectionResponse;

public interface ConnectMarketStreamPort {

    WebSocketConnectionResponse execute(String exchangeName, StreamSubscriptionRequest parameters);

}
