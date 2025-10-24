package com.marmitt.coinbase.processor.send;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marmitt.core.dto.common.CurrencyPair;
import com.marmitt.core.dto.websocket.request.MessageRequest;
import com.marmitt.core.dto.websocket.request.StreamSubscriptionRequest;
import com.marmitt.core.enums.MessageType;
import com.marmitt.core.enums.StreamAction;
import com.marmitt.core.ports.outbound.exchange.adapter.SenderSpecializedProcessorPort;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class StreamProcessor implements SenderSpecializedProcessorPort {

    private final ObjectMapper objectMapper;

    public StreamProcessor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String execute(MessageRequest request) {
        if (request instanceof StreamSubscriptionRequest streamRequest) {
            return processStreamSubscription(streamRequest);
        } else {
            throw new IllegalArgumentException(
                "Expected StreamSubscriptionRequest but received: " + request.getClass().getSimpleName()
            );
        }
    }

    @Override
    public boolean canProcess(MessageType messageType) {
        return MessageType.STREAM_SUBSCRIPTION.equals(messageType) || 
               MessageType.STREAM_UNSUBSCRIPTION.equals(messageType);
    }

    /**
     * Processa requests de subscription/unsubscription para streams da Coinbase.
     */
    private String processStreamSubscription(StreamSubscriptionRequest streamRequest) {
        try {
            // Determinar tipo da mensagem baseado na ação
            String messageType = streamRequest.getStreamAction() == StreamAction.SUBSCRIBE ? 
                "subscribe" : "unsubscribe";
            
            // Agrupar currency pairs por tipo de stream (Coinbase permite múltiplos tipos)
            Map<String, List<String>> channelsByType = groupCurrencyPairsByStreamType(streamRequest.getCurrencyPairs());
            
            // Construir a mensagem final
            Map<String, Object> message = new HashMap<>();
            message.put("type", messageType);
            message.put("channels", buildChannels(channelsByType));
            
            String jsonMessage = objectMapper.writeValueAsString(message);
            log.debug("Generated Coinbase {} stream message: {}", messageType, jsonMessage);
            
            return jsonMessage;
            
        } catch (Exception e) {
            log.error("Failed to process Coinbase stream subscription", e);
            throw new RuntimeException("Failed to process Coinbase stream subscription message", e);
        }
    }

    /**
     * Agrupa currency pairs por tipo de stream para otimizar a mensagem.
     */
    private Map<String, List<String>> groupCurrencyPairsByStreamType(List<CurrencyPair> currencyPairs) {
        Map<String, List<String>> grouped = new HashMap<>();
        
        for (CurrencyPair pair : currencyPairs) {
            String channelName = mapStreamTypeToChannelName(pair);
            String productId = buildCoinbaseProductId(pair);
            
            grouped.computeIfAbsent(channelName, k -> new java.util.ArrayList<>()).add(productId);
        }
        
        return grouped;
    }

    /**
     * Mapeia StreamType para nome do canal da Coinbase.
     */
    private String mapStreamTypeToChannelName(CurrencyPair currencyPair) {
        return switch (currencyPair.streamType()) {
            case TICKER -> "ticker";
            case TRADE -> "matches";  // Coinbase usa "matches" para trades
            case BOOK_TICKER -> "ticker"; // Coinbase ticker inclui bid/ask
            case DEPTH -> "level2";  // Coinbase usa "level2" para order book
        };
    }

    /**
     * Converte CurrencyPair para Product ID da Coinbase (formato: BASE-QUOTE).
     */
    private String buildCoinbaseProductId(CurrencyPair currencyPair) {
        return currencyPair.baseCurrency().toUpperCase() + "-" + currencyPair.quoteCurrency().toUpperCase();
    }

    /**
     * Constrói a estrutura de channels para a mensagem Coinbase.
     */
    private List<Map<String, Object>> buildChannels(Map<String, List<String>> channelsByType) {
        return channelsByType.entrySet().stream()
            .map(entry -> {
                Map<String, Object> channel = new HashMap<>();
                channel.put("name", entry.getKey());
                channel.put("product_ids", entry.getValue());
                return channel;
            })
            .toList();
    }
}