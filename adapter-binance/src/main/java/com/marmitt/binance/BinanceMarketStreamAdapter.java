package com.marmitt.binance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marmitt.binance.auth.BinanceCredentials;
import com.marmitt.binance.auth.BinanceRequestSigner;
import com.marmitt.binance.boot.BinanceBootReadinessChecker;
import com.marmitt.binance.processor.receive.BinanceReceivedMessageProcessor;
import com.marmitt.binance.processor.send.BinanceSenderMessageProcessor;
import com.marmitt.binance.rest.BinanceRestRequestBuilder;
import com.marmitt.core.dto.exchange.boot.ExchangeBootReadiness;
import com.marmitt.core.ports.outbound.http.HttpClientPort;
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

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class BinanceMarketStreamAdapter implements
        ExchangeStreamingPort,
        ExchangeOrderExecutionPort,
        ExchangeOrderQueryPort,
        ExchangeAccountQueryPort,
        ExchangeBootReadinessPort {

    private final BinanceReceivedMessageProcessor receivedMessageProcessor;
    private final BinanceSenderMessageProcessor senderMessageProcessor;
    private final BinanceUrlBuilder urlBuilder;
    private final BinanceBootReadinessChecker bootReadinessChecker;

    public BinanceMarketStreamAdapter(ObjectMapper objectMapper,
                                      BinanceConnectionConfig config,
                                      HttpClientPort httpClient) {
        var apiConfig         = new BinanceApiConfig(config.wsBaseUrl(), config.restBaseUrl());
        var credentials       = new BinanceCredentials(config.apiKey(), config.apiSecret());
        var signer            = new BinanceRequestSigner(credentials);
        var binanceUrlBuilder = new BinanceUrlBuilder(apiConfig);
        this.urlBuilder               = binanceUrlBuilder;
        this.senderMessageProcessor   = new BinanceSenderMessageProcessor(objectMapper, signer, binanceUrlBuilder);
        this.receivedMessageProcessor = new BinanceReceivedMessageProcessor(objectMapper);
        this.bootReadinessChecker     = new BinanceBootReadinessChecker(
                config.restBaseUrl(),
                new BinanceRestRequestBuilder(config.restBaseUrl(), signer),
                httpClient,
                objectMapper);
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
    public String buildConnectionUrl(StreamSubscriptionRequest parameters, String exchangeName) {
        return urlBuilder.buildConnectionUrl(parameters);
    }

    @Override
    public String formatMessage(MessageRequest request) {
        return senderMessageProcessor.execute(request);
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
        try {
            return CompletableFuture.supplyAsync(bootReadinessChecker::check)
                    .get(10, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            return ExchangeBootReadiness.notReady("BINANCE", "CONNECTIVITY_FAILURE",
                    "Boot readiness check timed out after 10s");
        } catch (ExecutionException e) {
            return ExchangeBootReadiness.notReady("BINANCE", "UNKNOWN_ERROR",
                    "Boot readiness check failed: " + e.getCause().getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ExchangeBootReadiness.notReady("BINANCE", "UNKNOWN_ERROR",
                    "Boot readiness check interrupted");
        }
    }

    private UnsupportedOperationException restNotImplemented() {
        return new UnsupportedOperationException(
                "BINANCE REST not yet wired — pending use case implementation."
        );
    }
}
