package com.marmitt.coinbase.listener;

import com.marmitt.core.ports.outbound.websocket.MessageProcessorPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marmitt.coinbase.request.TickerStreamRequest;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.List;

@Slf4j
public class CoinbaseWebSocketListener implements MessageProcessorPort {
    
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void onMessage(String message) {
        log.info("Coinbase - Received message: {}", message);

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