package com.marmitt.coinbase;

import com.marmitt.core.dto.websocket.request.WebSocketConnectionParametersRequest;
import com.marmitt.core.ports.outbound.exchange.adapter.ExchangeUrlBuilderPort;

public class CoinbaseUrlBuilder implements ExchangeUrlBuilderPort {

    @Override
    public String buildConnectionUrl(WebSocketConnectionParametersRequest parameters) {
        return CoinbaseConfiguration.BASE_URL;
    }
}