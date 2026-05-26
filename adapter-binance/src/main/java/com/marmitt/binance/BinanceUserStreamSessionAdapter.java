package com.marmitt.binance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marmitt.binance.auth.BinanceCredentials;
import com.marmitt.binance.userdata.BinanceUserStreamSession;
import com.marmitt.binance.userdata.ListenKeyManager;
import com.marmitt.core.ports.outbound.exchange.streaming.UserStreamSession;
import com.marmitt.core.ports.outbound.exchange.streaming.UserStreamSessionPort;
import com.marmitt.core.ports.outbound.http.HttpClientPort;

import java.util.UUID;

public class BinanceUserStreamSessionAdapter implements UserStreamSessionPort {

    private final String wsBaseUrl;
    private final String restBaseUrl;
    private final BinanceCredentials credentials;
    private final HttpClientPort httpClient;
    private final ObjectMapper objectMapper;

    public BinanceUserStreamSessionAdapter(BinanceConnectionConfig config,
                                           HttpClientPort httpClient,
                                           ObjectMapper objectMapper) {
        this.wsBaseUrl = config.wsBaseUrl();
        this.restBaseUrl = config.restBaseUrl();
        this.credentials = new BinanceCredentials(config.apiKey(), config.apiSecret());
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getExchangeName() {
        return "BINANCE";
    }

    @Override
    public UserStreamSession createSession(UUID connectionId) {
        var listenKeyManager = new ListenKeyManager(restBaseUrl, credentials, httpClient, objectMapper);
        return new BinanceUserStreamSession(listenKeyManager, wsBaseUrl);
    }
}
