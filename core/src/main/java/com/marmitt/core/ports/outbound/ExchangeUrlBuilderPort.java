package com.marmitt.core.ports.outbound;

import com.marmitt.core.dto.configuration.WebSocketConnectionParameters;

public interface ExchangeUrlBuilderPort {

    String buildConnectionUrl(WebSocketConnectionParameters parameters);
}