package com.marmitt.application.spring.config.exchange;

import com.marmitt.binance.rest.BinanceRestAdapter;
import com.marmitt.core.dto.exchange.boot.ExchangeBootReadiness;
import com.marmitt.core.dto.websocket.data.AccountDataDto;
import com.marmitt.core.dto.websocket.data.OrderDataDto;
import com.marmitt.core.dto.websocket.request.SendCancelOrderRequest;
import com.marmitt.core.dto.websocket.request.SendOrderRequest;
import com.marmitt.core.ports.outbound.exchange.adapter.ExchangeUrlBuilderPort;
import com.marmitt.core.ports.outbound.exchange.adapter.ReceivedMessageProcessorPort;
import com.marmitt.core.ports.outbound.exchange.adapter.SenderMessageProcessorPort;
import com.marmitt.core.ports.outbound.exchange.rest.ExchangeAccountQueryPort;
import com.marmitt.core.ports.outbound.exchange.rest.ExchangeBootReadinessPort;
import com.marmitt.core.ports.outbound.exchange.rest.ExchangeOrderExecutionPort;
import com.marmitt.core.ports.outbound.exchange.rest.ExchangeOrderQueryPort;
import com.marmitt.core.ports.outbound.exchange.streaming.ExchangeStreamingPort;
import com.marmitt.core.ports.outbound.websocket.WebSocketPort;

import java.util.List;
import java.util.Optional;

public class BinanceMarketStreamAdapter implements
        ExchangeStreamingPort,
        ExchangeOrderExecutionPort,
        ExchangeOrderQueryPort,
        ExchangeAccountQueryPort,
        ExchangeBootReadinessPort {

    private final WebSocketPort webSocketPort;
    private final ReceivedMessageProcessorPort receivedMessageProcessor;
    private final SenderMessageProcessorPort senderMessageProcessor;
    private final ExchangeUrlBuilderPort urlBuilder;
    private final BinanceRestAdapter restAdapter;

    public BinanceMarketStreamAdapter(WebSocketPort webSocketPort,
                                      ReceivedMessageProcessorPort receivedMessageProcessor,
                                      SenderMessageProcessorPort senderMessageProcessor,
                                      ExchangeUrlBuilderPort urlBuilder,
                                      BinanceRestAdapter restAdapter) {
        this.webSocketPort = webSocketPort;
        this.receivedMessageProcessor = receivedMessageProcessor;
        this.senderMessageProcessor = senderMessageProcessor;
        this.urlBuilder = urlBuilder;
        this.restAdapter = restAdapter;
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
        return restAdapter.submitOrder(request);
    }

    @Override
    public OrderDataDto cancelOrder(SendCancelOrderRequest request) {
        return restAdapter.cancelOrder(request);
    }

    @Override
    public Optional<OrderDataDto> queryOrderByClientOrderId(String symbol, String clientOrderId) {
        return restAdapter.queryOrderByClientOrderId(symbol, clientOrderId);
    }

    @Override
    public Optional<OrderDataDto> queryOrderByExchangeOrderId(String symbol, String exchangeOrderId) {
        return restAdapter.queryOrderByExchangeOrderId(symbol, exchangeOrderId);
    }

    @Override
    public List<OrderDataDto> listOpenOrdersBySymbol(String symbol) {
        return restAdapter.listOpenOrdersBySymbol(symbol);
    }

    @Override
    public List<OrderDataDto> listAllOpenOrders() {
        return restAdapter.listAllOpenOrders();
    }

    @Override
    public AccountDataDto queryAccountSnapshot() {
        return restAdapter.queryAccountSnapshot();
    }

    @Override
    public ExchangeBootReadiness checkBootReadiness() {
        return ExchangeBootReadiness.ready("BINANCE", "Binance market stream adapter initialized.");
    }
}
