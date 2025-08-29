package com.marmitt.ctrade.domain.dto;

import com.marmitt.ctrade.domain.port.ExchangePort;
import com.marmitt.ctrade.domain.port.ExchangeWebSocketAdapter;

public record StrategySetup(
        ExchangePort exchangePort,
        ExchangeWebSocketAdapter exchangeWebSocketAdapter
) {
}
