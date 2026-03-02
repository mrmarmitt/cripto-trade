package com.marmitt.application.spring.config.exchange;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marmitt.application.spring.adapter.OkHttp3ListenerConverter;
import com.marmitt.application.spring.adapter.OkHttp3WebSocketAdapter;
import com.marmitt.binance.BinanceUrlBuilder;
import com.marmitt.binance.processor.receive.BinanceReceivedMessageProcessor;
import com.marmitt.binance.processor.send.BinanceSenderMessageProcessor;
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
 * Binance adapter.
 *
 * <p>Stage 2 capability status:
 * <ul>
 *   <li>Streaming: implemented.</li>
 *   <li>REST capabilities: declared and explicit as not implemented yet.</li>
 * </ul>
 */
public class BinanceExchangeAdapter implements
        ExchangeStreamingPort,
        ExchangeOrderExecutionPort,
        ExchangeOrderQueryPort,
        ExchangeAccountQueryPort {

    private final WebSocketPort webSocketPort;
    private final ReceivedMessageProcessorPort receivedMessageProcessor;
    private final SenderMessageProcessorPort senderMessageProcessor;
    private final ExchangeUrlBuilderPort urlBuilder;

    public BinanceExchangeAdapter(ObjectMapper objectMapper, EventPublisherPort eventPublisher) {
        this.webSocketPort = new OkHttp3WebSocketAdapter(new OkHttp3ListenerConverter(eventPublisher));
        this.receivedMessageProcessor = new BinanceReceivedMessageProcessor(objectMapper);
        this.senderMessageProcessor = new BinanceSenderMessageProcessor(objectMapper);
        this.urlBuilder = new BinanceUrlBuilder();
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
                "BINANCE REST capability is not implemented yet. Use streaming path for now."
        );
    }
}
