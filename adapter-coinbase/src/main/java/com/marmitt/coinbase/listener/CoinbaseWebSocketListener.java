package com.marmitt.coinbase.listener;

import com.marmitt.core.ports.outbound.WebSocketListenerPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marmitt.coinbase.request.TickerStreamRequest;

import java.util.Arrays;
import java.util.List;

public class CoinbaseWebSocketListener implements WebSocketListenerPort {
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    private boolean subscribed = false;
    
    @Override
    public void onMessage(String message) {
        System.out.println("Coinbase - Received message: " + message);
        
        // Se receber confirmação de subscrição, marca como subscrito
        if (message.contains("\"type\":\"subscriptions\"")) {
            subscribed = true;
        }
    }
    
    public String createSubscribeMessage(String symbols) {
        try {
            // Converte string "BTC-USD,ETH-USD" para lista
            List<String> productIds = Arrays.asList(symbols.split(","));
            
            TickerStreamRequest request = TickerStreamRequest.subscribe(productIds);
            return objectMapper.writeValueAsString(request);
        } catch (Exception e) {
            System.err.println("Error creating subscribe message: " + e.getMessage());
            return "{\"type\":\"subscribe\",\"product_ids\":[\"BTC-USD\"],\"channels\":[\"ticker\"]}";
        }
    }
}