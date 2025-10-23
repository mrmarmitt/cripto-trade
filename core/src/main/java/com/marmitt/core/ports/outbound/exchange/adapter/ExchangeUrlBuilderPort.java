package com.marmitt.core.ports.outbound.exchange.adapter;

import com.marmitt.core.dto.websocket.request.StreamSubscriptionRequest;

public interface ExchangeUrlBuilderPort {

    String buildConnectionUrl(StreamSubscriptionRequest parameters);
}