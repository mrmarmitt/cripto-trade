package com.marmitt.binance.userdata;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marmitt.binance.auth.BinanceRequestSigner;
import com.marmitt.core.ports.outbound.exchange.streaming.UserStreamSession;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
public class BinanceUserStreamSession implements UserStreamSession {

    private final String wsApiBaseUrl;
    private final BinanceRequestSigner signer;
    private final ObjectMapper objectMapper;

    public BinanceUserStreamSession(String wsApiBaseUrl, BinanceRequestSigner signer, ObjectMapper objectMapper) {
        this.wsApiBaseUrl = wsApiBaseUrl;
        this.signer = signer;
        this.objectMapper = objectMapper;
    }

    @Override
    public String open() {
        return wsApiBaseUrl;
    }

    @Override
    public Optional<String> subscriptionMessage() {
        try {
            Map<String, Object> params = signer.signWebSocketParams(Map.of("recvWindow", 5000L));
            Map<String, Object> message = Map.of(
                    "id", UUID.randomUUID().toString(),
                    "method", "userDataStream.subscribe.signature",
                    "params", params
            );
            return Optional.of(objectMapper.writeValueAsString(message));
        } catch (Exception e) {
            log.error("Failed to generate user data stream subscription message", e);
            return Optional.empty();
        }
    }

    @Override
    public void close() {
        log.info("Binance user data stream session closed");
    }
}
