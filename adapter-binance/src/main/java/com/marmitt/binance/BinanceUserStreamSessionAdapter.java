package com.marmitt.binance;

import com.marmitt.core.ports.outbound.exchange.streaming.UserStreamSessionPort;

public class BinanceUserStreamSessionAdapter implements UserStreamSessionPort {

    private final String wsBaseUrl;

    public BinanceUserStreamSessionAdapter(BinanceConnectionConfig config) {
        this.wsBaseUrl = config.wsBaseUrl();
    }

    @Override
    public String getExchangeName() {
        return "BINANCE";
    }

    @Override
    public String buildConnectionUrl(String credential) {
        return wsBaseUrl + "/ws/" + credential;
    }
}
