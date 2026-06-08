package com.marmitt.binance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marmitt.binance.auth.BinanceCredentials;
import com.marmitt.binance.auth.BinanceRequestSigner;
import com.marmitt.binance.userdata.BinanceUserStreamSession;
import com.marmitt.core.ports.outbound.exchange.streaming.UserStreamSession;
import com.marmitt.core.ports.outbound.exchange.streaming.UserStreamSessionPort;

import java.util.UUID;

public class BinanceUserStreamSessionAdapter implements UserStreamSessionPort {

    private final String wsApiBaseUrl;
    private final BinanceRequestSigner signer;
    private final ObjectMapper objectMapper;

    public BinanceUserStreamSessionAdapter(String wsApiBaseUrl, BinanceCredentials credentials, ObjectMapper objectMapper) {
        this.wsApiBaseUrl = wsApiBaseUrl;
        this.signer = new BinanceRequestSigner(credentials);
        this.objectMapper = objectMapper;
    }

    @Override
    public String getExchangeName() {
        return "BINANCE";
    }

    @Override
    public UserStreamSession createSession(UUID connectionId) {
        return new BinanceUserStreamSession(wsApiBaseUrl, signer, objectMapper);
    }
}
