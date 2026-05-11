package com.marmitt.application.spring.config.exchange;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marmitt.application.spring.adapter.OkHttp3ListenerConverter;
import com.marmitt.application.spring.adapter.OkHttp3WebSocketAdapter;
import com.marmitt.coinbase.CoinbaseUrlBuilder;
import com.marmitt.coinbase.processor.receive.CoinbaseReceivedMessageProcessor;
import com.marmitt.coinbase.processor.send.CoinbaseSenderMessageProcessor;
import com.marmitt.core.dto.websocket.data.AccountDataDto;
import com.marmitt.core.dto.websocket.data.OrderDataDto;
import com.marmitt.core.dto.websocket.request.SendCancelOrderRequest;
import com.marmitt.core.dto.websocket.request.SendOrderRequest;
import com.marmitt.core.dto.exchange.boot.ExchangeBootReadiness;
import com.marmitt.core.enums.StreamChannel;
import com.marmitt.core.dto.processing.ProcessingResult;
import com.marmitt.core.dto.websocket.MessageContext;
import com.marmitt.core.dto.websocket.data.ProcessorResponse;
import com.marmitt.core.dto.websocket.request.MessageRequest;
import com.marmitt.core.dto.websocket.request.StreamSubscriptionRequest;
import com.marmitt.core.ports.outbound.events.EventPublisherPort;
import com.marmitt.core.ports.outbound.exchange.adapter.ExchangeUrlBuilderPort;
import com.marmitt.core.ports.outbound.exchange.adapter.ReceivedMessageProcessorPort;
import com.marmitt.core.ports.outbound.exchange.adapter.SenderMessageProcessorPort;
import com.marmitt.core.ports.outbound.exchange.rest.ExchangeAccountQueryPort;
import com.marmitt.core.ports.outbound.exchange.rest.ExchangeBootReadinessPort;
import com.marmitt.core.ports.outbound.exchange.rest.ExchangeOrderExecutionPort;
import com.marmitt.core.ports.outbound.exchange.rest.ExchangeOrderQueryPort;
import com.marmitt.core.ports.outbound.exchange.streaming.ExchangeStreamingPort;
import com.marmitt.core.ports.outbound.websocket.WebSocketPort;

import java.util.UUID;

import java.util.List;
import java.util.Optional;

/**
 * Coinbase adapter.
 *
 * <p>Stage 2 capability status:
 * <ul>
 *   <li>Streaming: implemented.</li>
 *   <li>REST capabilities: declared and explicit as not implemented yet.</li>
 * </ul>
 */
public class CoinbaseExchangeAdapter implements
        ExchangeStreamingPort,
        ExchangeOrderExecutionPort,
        ExchangeOrderQueryPort,
        ExchangeAccountQueryPort,
        ExchangeBootReadinessPort {

    private final WebSocketPort webSocketPort;
    private final ReceivedMessageProcessorPort receivedMessageProcessor;
    private final SenderMessageProcessorPort senderMessageProcessor;
    private final ExchangeUrlBuilderPort urlBuilder;

    public CoinbaseExchangeAdapter(ObjectMapper objectMapper, EventPublisherPort eventPublisher) {
        this.webSocketPort = new OkHttp3WebSocketAdapter(new OkHttp3ListenerConverter(eventPublisher, StreamChannel.MARKET));
        this.receivedMessageProcessor = new CoinbaseReceivedMessageProcessor(objectMapper);
        this.senderMessageProcessor = new CoinbaseSenderMessageProcessor(objectMapper);
        this.urlBuilder = new CoinbaseUrlBuilder();
    }

    @Override
    public String getExchangeName() {
        return "COINBASE";
    }

    @Override
    public boolean requiresPostConnection() {
        return true;
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
        if (webSocketPort == null || receivedMessageProcessor == null || senderMessageProcessor == null || urlBuilder == null) {
            return ExchangeBootReadiness.notReady("COINBASE", "MISSING_COMPONENT", "Coinbase adapter components are not initialized.");
        }
        return ExchangeBootReadiness.ready("COINBASE", "Coinbase adapter initialized for streaming.");
    }

    private UnsupportedOperationException restNotImplemented() {
        return new UnsupportedOperationException(
                "COINBASE REST capability is not implemented yet. Use streaming path for now."
        );
    }
}
