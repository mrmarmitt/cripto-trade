package com.marmitt.binance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marmitt.binance.auth.BinanceCredentials;
import com.marmitt.binance.auth.BinanceRequestSigner;
import com.marmitt.binance.processor.receive.BinanceReceivedMessageProcessor;
import com.marmitt.binance.processor.send.BinanceSenderMessageProcessor;
import com.marmitt.core.dto.exchange.boot.ExchangeBootReadiness;
import com.marmitt.core.dto.processing.ProcessingResult;
import com.marmitt.core.dto.websocket.MessageContext;
import com.marmitt.core.dto.websocket.data.AccountDataDto;
import com.marmitt.core.dto.websocket.data.OrderDataDto;
import com.marmitt.core.dto.websocket.data.ProcessorResponse;
import com.marmitt.core.dto.websocket.request.MessageRequest;
import com.marmitt.core.dto.websocket.request.SendCancelOrderRequest;
import com.marmitt.core.dto.websocket.request.SendOrderRequest;
import com.marmitt.core.dto.websocket.request.StreamSubscriptionRequest;
import com.marmitt.core.ports.outbound.exchange.rest.ExchangeAccountQueryPort;
import com.marmitt.core.ports.outbound.exchange.rest.ExchangeBootReadinessPort;
import com.marmitt.core.ports.outbound.exchange.rest.ExchangeOrderExecutionPort;
import com.marmitt.core.ports.outbound.exchange.rest.ExchangeOrderQueryPort;
import com.marmitt.core.ports.outbound.exchange.streaming.ExchangeStreamingPort;
import com.marmitt.core.ports.outbound.websocket.WebSocketPort;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class BinanceMarketStreamAdapter implements
        ExchangeStreamingPort,
        ExchangeOrderExecutionPort,
        ExchangeOrderQueryPort,
        ExchangeAccountQueryPort,
        ExchangeBootReadinessPort {

    private final WebSocketPort webSocketPort;
    private final BinanceReceivedMessageProcessor receivedMessageProcessor;
    private final BinanceSenderMessageProcessor senderMessageProcessor;
    private final BinanceUrlBuilder urlBuilder;

    public BinanceMarketStreamAdapter(WebSocketPort webSocketPort,
                                      ObjectMapper objectMapper,
                                      BinanceConnectionConfig config) {
        var apiConfig         = new BinanceApiConfig(config.wsBaseUrl(), config.restBaseUrl());
        var credentials       = new BinanceCredentials(config.apiKey(), config.apiSecret());
        var signer            = new BinanceRequestSigner(credentials);
        var binanceUrlBuilder = new BinanceUrlBuilder(apiConfig);
        this.urlBuilder               = binanceUrlBuilder;
        this.senderMessageProcessor   = new BinanceSenderMessageProcessor(objectMapper, signer, binanceUrlBuilder);
        this.receivedMessageProcessor = new BinanceReceivedMessageProcessor(objectMapper);
        this.webSocketPort            = webSocketPort;
    }

    @Override
    public String getExchangeName() {
        return "BINANCE";
    }

    @Override
    public boolean requiresPostConnection() {
        return false;
    }

    @Override
    public void connect(StreamSubscriptionRequest parameters, String exchangeName, UUID connectionId) {
        String url = urlBuilder.buildConnectionUrl(parameters);
        webSocketPort.connect(url, exchangeName, connectionId);
    }

    @Override
    public void disconnect(String exchangeName, UUID connectionId) {
        webSocketPort.disconnect(exchangeName, connectionId);
    }

    @Override
    public void sendMessage(MessageRequest request) {
        String message = senderMessageProcessor.execute(request);
        webSocketPort.sendMessage(message);
    }

    @Override
    public ProcessingResult<? extends ProcessorResponse> processMessage(String rawMessage, MessageContext context) {
        return receivedMessageProcessor.processMessage(rawMessage, context);
    }

    @Override
    public OrderDataDto submitOrder(SendOrderRequest request) {
        throw restNotImplemented();
    }

    @Override
    public OrderDataDto cancelOrder(SendCancelOrderRequest request) {
        throw restNotImplemented();
    }

    @Override
    public Optional<OrderDataDto> queryOrderByClientOrderId(String symbol, String clientOrderId) {
        throw restNotImplemented();
    }

    @Override
    public Optional<OrderDataDto> queryOrderByExchangeOrderId(String symbol, String exchangeOrderId) {
        throw restNotImplemented();
    }

    @Override
    public List<OrderDataDto> listOpenOrdersBySymbol(String symbol) {
        throw restNotImplemented();
    }

    @Override
    public List<OrderDataDto> listAllOpenOrders() {
        throw restNotImplemented();
    }

    @Override
    public AccountDataDto queryAccountSnapshot() {
        throw restNotImplemented();
    }

    @Override
    public ExchangeBootReadiness checkBootReadiness() {
        return ExchangeBootReadiness.ready("BINANCE", "Binance market stream adapter initialized.");
    }

    private UnsupportedOperationException restNotImplemented() {
        return new UnsupportedOperationException(
                "BINANCE REST not yet wired — pending use case implementation."
        );
    }
}
