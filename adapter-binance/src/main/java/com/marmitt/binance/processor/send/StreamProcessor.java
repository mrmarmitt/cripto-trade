package com.marmitt.binance.processor.send;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marmitt.binance.BinanceUrlBuilder;
import com.marmitt.core.dto.common.CurrencyPair;
import com.marmitt.core.dto.websocket.request.MessageRequest;
import com.marmitt.core.dto.websocket.request.StreamSubscriptionRequest;
import com.marmitt.core.enums.MessageType;
import com.marmitt.core.enums.StreamAction;
import com.marmitt.core.ports.outbound.exchange.adapter.SenderSpecializedProcessorPort;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StreamProcessor implements SenderSpecializedProcessorPort {

    private final ObjectMapper objectMapper;
    private final BinanceUrlBuilder urlBuilder;

    public StreamProcessor(ObjectMapper objectMapper, BinanceUrlBuilder urlBuilder) {
        this.objectMapper = objectMapper;
        this.urlBuilder = urlBuilder;
    }

    @Override
    public String execute(MessageRequest request) {
        if (request instanceof StreamSubscriptionRequest streamRequest) {
            return processStreamSubscription(streamRequest);
        } else {
            throw new IllegalArgumentException("Expected SendStreamSubscriptionRequest or SendTickerSubscriptionRequest but received: " + request.getClass().getSimpleName());
        }
    }

    @Override
    public boolean canProcess(MessageType messageType) {
        return MessageType.STREAM_SUBSCRIPTION.equals(messageType) || MessageType.STREAM_UNSUBSCRIPTION.equals(messageType);
    }

    private String processStreamSubscription(StreamSubscriptionRequest streamRequest) {

        try {
            List<String> streams = streamRequest.getCurrencyPairs().stream()
                    .map(urlBuilder::buildStreamName)
                    .toList();

            Map<String, Object> message = new HashMap<>();
            message.put("method", streamRequest.getStreamAction() == StreamAction.SUBSCRIBE ? "SUBSCRIBE" : "UNSUBSCRIBE");
            message.put("params", streams.toArray(new String[0]));
            message.put("id", System.currentTimeMillis());

            return objectMapper.writeValueAsString(message);
        } catch (Exception e) {
            throw new RuntimeException("Failed to process stream subscription errorMessage", e);
        }
    }

}
