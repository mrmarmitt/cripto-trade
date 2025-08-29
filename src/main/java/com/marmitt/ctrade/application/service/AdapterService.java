package com.marmitt.ctrade.application.service;

import com.marmitt.ctrade.domain.port.ExchangePort;
import com.marmitt.ctrade.domain.port.WebSocketPort;
import com.marmitt.ctrade.infrastructure.exchange.ExchangeConnectionAdapter;
import com.marmitt.ctrade.infrastructure.exchange.ExchangePortAdapter;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Component
public class AdapterService {

    private final Map<ExchangeConnectionAdapter, WebSocketPort> connectionAdapters;
    private final Map<ExchangePortAdapter, ExchangePort> exchangePortAdapters;

    public AdapterService(Map<ExchangeConnectionAdapter, WebSocketPort> connectionAdapters, Map<ExchangePortAdapter, ExchangePort> exchangePortAdapters) {
        this.connectionAdapters = connectionAdapters;
        this.exchangePortAdapters = exchangePortAdapters;
    }

    public WebSocketPort get(ExchangeConnectionAdapter exchangeConnectionAdapter) {
        if (Objects.isNull(connectionAdapters))
            throw new RuntimeException("ConnectionAdapters is null.");

        return Optional.ofNullable(connectionAdapters.get(exchangeConnectionAdapter))
                .orElseThrow(() -> new RuntimeException("There is no WebSocketPort to exchangeConnectionAdapter: " + exchangeConnectionAdapter));
    }

    public ExchangePort get(ExchangePortAdapter exchangePortAdapter) {
        if (Objects.isNull(exchangePortAdapters))
            throw new RuntimeException("ExchangePortAdapters is null.");

        return Optional.ofNullable(exchangePortAdapters.get(exchangePortAdapter))
                .orElseThrow(() -> new RuntimeException("There is no ExchangePort to exchangePortAdapter: " + exchangePortAdapter));
    }
}
