package com.marmitt.binance.listener;

import com.marmitt.core.ports.outbound.WebSocketListenerPort;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BinanceWebSocketListener implements WebSocketListenerPort {
    @Override
    public void onMessage(String message) {
        log.info("Binance - Received message: {}", message);
    }
}
