package com.marmitt.coinbase;

import com.marmitt.core.dto.websocket.request.StreamSubscriptionRequest;
import com.marmitt.core.ports.outbound.exchange.adapter.ExchangeUrlBuilderPort;

public class CoinbaseUrlBuilder implements ExchangeUrlBuilderPort {

    @Override
    public String buildConnectionUrl(StreamSubscriptionRequest parameters) {
        return CoinbaseConfiguration.BASE_URL;
    }
}