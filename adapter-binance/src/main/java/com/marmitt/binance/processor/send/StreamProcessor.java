package com.marmitt.binance.processor.send;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marmitt.core.dto.common.CurrencyPair;
import com.marmitt.core.dto.websocket.request.SendMessageRequest;
import com.marmitt.core.dto.websocket.request.SendStreamRequest;
import com.marmitt.core.enums.MessageType;
import com.marmitt.core.enums.StreamAction;
import com.marmitt.core.enums.StreamType;
import com.marmitt.core.ports.outbound.exchange.adapter.SenderSpecializedProcessorPort;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StreamProcessor implements SenderSpecializedProcessorPort {

    private final ObjectMapper objectMapper;

    public StreamProcessor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String execute(SendMessageRequest request) {
        if (request instanceof SendStreamRequest streamRequest) {
            return processStreamSubscription(streamRequest);
        } else {
            throw new IllegalArgumentException("Expected SendStreamSubscriptionRequest or SendTickerSubscriptionRequest but received: " + request.getClass().getSimpleName());
        }
    }

    @Override
    public boolean canProcess(MessageType messageType) {
        return MessageType.STREAM_SUBSCRIPTION.equals(messageType) || MessageType.STREAM_UNSUBSCRIPTION.equals(messageType);
    }

    private String processStreamSubscription(SendStreamRequest streamRequest) {

        try {
            List<String> streams = streamRequest.getCurrencyPairs().stream()
                    .map(this::buildStreamName)
                    .toList();

            Map<String, Object> message = new HashMap<>();
            message.put("method", streamRequest.getStreamAction() == StreamAction.SUBSCRIBE ? "SUBSCRIBE" : "UNSUBSCRIBE");
            message.put("params", streams.toArray(new String[0]));
            message.put("id", System.currentTimeMillis());

            return objectMapper.writeValueAsString(message);
        } catch (Exception e) {
            throw new RuntimeException("Failed to process stream subscription message", e);
        }
    }

    private String buildStreamName(CurrencyPair currencyPair) {
        String lowerSymbol = (currencyPair.baseCurrency() + currencyPair.quoteCurrency()).toLowerCase();
        return switch (currencyPair.streamType()) {
            case TICKER -> lowerSymbol + "@ticker";
            case TRADE -> lowerSymbol + "@trade";
            case BOOK_TICKER -> lowerSymbol + "@bookTicker";
            case DEPTH -> lowerSymbol + "@depth";
        };
    }


}