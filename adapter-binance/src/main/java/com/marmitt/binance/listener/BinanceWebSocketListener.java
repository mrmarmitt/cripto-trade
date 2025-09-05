package com.marmitt.binance.listener;

import com.marmitt.core.ports.outbound.WebSocketListenerPort;

public class BinanceWebSocketListener implements WebSocketListenerPort {
    @Override
    public void onMessage(String message) {
        System.out.println("Binance - Received message: " + message);
    }
}
