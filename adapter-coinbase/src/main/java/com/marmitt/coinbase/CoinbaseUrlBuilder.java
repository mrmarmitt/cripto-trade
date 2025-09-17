package com.marmitt.coinbase;

import com.marmitt.core.dto.configuration.WebSocketConnectionParameters;
import com.marmitt.core.ports.outbound.ExchangeUrlBuilderPort;

public class CoinbaseUrlBuilder implements ExchangeUrlBuilderPort {

    @Override
    public String buildConnectionUrl(WebSocketConnectionParameters parameters) {
        return CoinbaseConfiguration.BASE_URL;
    }
}