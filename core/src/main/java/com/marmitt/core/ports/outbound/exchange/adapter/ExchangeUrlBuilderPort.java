package com.marmitt.core.ports.outbound.exchange.adapter;

import com.marmitt.core.dto.websocket.request.WebSocketConnectionParametersRequest;

public interface ExchangeUrlBuilderPort {

    String buildConnectionUrl(WebSocketConnectionParametersRequest parameters);
}