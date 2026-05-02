package com.marmitt.coinbase.processor.send;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marmitt.core.dto.websocket.request.MessageRequest;
import com.marmitt.core.ports.outbound.exchange.adapter.SenderMessageProcessorPort;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class CoinbaseSenderMessageProcessor implements SenderMessageProcessorPort {

    private final List<CoinbaseSenderProcessor> specializedProcessors;

    public CoinbaseSenderMessageProcessor(ObjectMapper objectMapper) {
        this.specializedProcessors = new ArrayList<>();
        initializeProcessors(objectMapper);
    }

    private void initializeProcessors(ObjectMapper objectMapper) {
        specializedProcessors.add(new StreamProcessor(objectMapper));
        specializedProcessors.add(new OrderProcessor(objectMapper));
        specializedProcessors.add(new CancelOrderProcessor(objectMapper));
    }

    @Override
    public String execute(MessageRequest request) {
        return specializedProcessors.stream()
                .filter(processor -> processor.canProcess(request.getMessageType()))
                .findFirst()
                .map(processor -> processor.execute(request))
                .orElseThrow(() -> new IllegalArgumentException(
                        "No specialized processor found for errorMessage type: " + request.getMessageType()));
    }
}