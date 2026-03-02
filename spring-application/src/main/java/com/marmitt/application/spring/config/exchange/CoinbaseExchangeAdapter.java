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
import com.marmitt.core.ports.outbound.events.EventPublisherPort;
import com.marmitt.core.ports.outbound.exchange.adapter.ExchangeUrlBuilderPort;
import com.marmitt.core.ports.outbound.exchange.adapter.ReceivedMessageProcessorPort;
import com.marmitt.core.ports.outbound.exchange.adapter.SenderMessageProcessorPort;
import com.marmitt.core.ports.outbound.exchange.rest.ExchangeAccountQueryPort;
import com.marmitt.core.ports.outbound.exchange.rest.ExchangeOrderExecutionPort;
import com.marmitt.core.ports.outbound.exchange.rest.ExchangeOrderQueryPort;
import com.marmitt.core.ports.outbound.exchange.streaming.ExchangeStreamingPort;
import com.marmitt.core.ports.outbound.websocket.WebSocketPort;

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
        ExchangeAccountQueryPort {

    private final WebSocketPort webSocketPort;
    private final ReceivedMessageProcessorPort receivedMessageProcessor;
    private final SenderMessageProcessorPort senderMessageProcessor;
    private final ExchangeUrlBuilderPort urlBuilder;

    public CoinbaseExchangeAdapter(ObjectMapper objectMapper, EventPublisherPort eventPublisher) {
        this.webSocketPort = new OkHttp3WebSocketAdapter(new OkHttp3ListenerConverter(eventPublisher));
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
    public WebSocketPort getWebSocketPort() {
        return webSocketPort;
    }

    @Override
    public ReceivedMessageProcessorPort getReceivedMessageProcessor() {
        return receivedMessageProcessor;
    }

    @Override
    public SenderMessageProcessorPort getSenderMessageProcessor() {
        return senderMessageProcessor;
    }

    @Override
    public ExchangeUrlBuilderPort getUrlBuilder() {
        return urlBuilder;
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

    private UnsupportedOperationException restNotImplemented() {
        return new UnsupportedOperationException(
                "COINBASE REST capability is not implemented yet. Use streaming path for now."
        );
    }
}
