package com.marmitt.binance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marmitt.binance.processor.receive.BinanceUserDataProcessor;
import com.marmitt.core.dto.processing.ProcessingResult;
import com.marmitt.core.dto.websocket.MessageContext;
import com.marmitt.core.dto.websocket.data.ProcessorResponse;
import com.marmitt.core.ports.outbound.exchange.streaming.ExchangeUserStreamPort;

public class BinanceUserStreamAdapter implements ExchangeUserStreamPort {

    private final BinanceUserDataProcessor receivedMessageProcessor;

    public BinanceUserStreamAdapter(ObjectMapper objectMapper) {
        this.receivedMessageProcessor = new BinanceUserDataProcessor(objectMapper);
    }

    @Override
    public String getExchangeName() {
        return "BINANCE";
    }

    @Override
    public ProcessingResult<? extends ProcessorResponse> processMessage(String rawMessage, MessageContext context) {
        return receivedMessageProcessor.processMessage(rawMessage, context);
    }
}
