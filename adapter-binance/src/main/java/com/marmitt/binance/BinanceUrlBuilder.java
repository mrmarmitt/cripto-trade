package com.marmitt.binance;

import com.marmitt.core.dto.common.CurrencyPair;
import com.marmitt.core.dto.websocket.request.StreamSubscriptionRequest;
import com.marmitt.core.ports.outbound.exchange.adapter.ExchangeUrlBuilderPort;

import java.util.List;
import java.util.stream.Collectors;

public class BinanceUrlBuilder implements ExchangeUrlBuilderPort {

    private final BinanceApiConfig config;

    public BinanceUrlBuilder(BinanceApiConfig config) {
        this.config = config;
    }

    @Override
    public String buildConnectionUrl(StreamSubscriptionRequest parameters) {
        List<CurrencyPair> currencyPairs = parameters.getCurrencyPairs();
        if (currencyPairs.isEmpty()) {
            throw new IllegalArgumentException("At least one currency pair is required");
        }

        if (currencyPairs.size() == 1) {
            String streamName = buildStreamName(currencyPairs.getFirst());
            return config.getWebSocketBaseUrl() + BinanceApiConfig.SINGLE_STREAM_PATH + "/" + streamName;
        }

        String streamQuery = currencyPairs.stream()
                .map(this::buildStreamName)
                .collect(Collectors.joining("/"));

        return config.getWebSocketBaseUrl() + BinanceApiConfig.COMBINED_STREAM_PATH + "?streams=" + streamQuery;
    }

    public String getWebSocketBaseUrl() {
        return config.getWebSocketBaseUrl();
    }

    public String getRestBaseUrl() {
        return config.getRestBaseUrl();
    }

    public String buildStreamName(CurrencyPair currencyPair) {
        String lowerSymbol = (currencyPair.baseCurrency() + currencyPair.quoteCurrency()).toLowerCase();
        return switch (currencyPair.streamType()) {
            case TICKER     -> lowerSymbol + "@ticker";
            case TRADE      -> lowerSymbol + "@trade";
            case BOOK_TICKER -> lowerSymbol + "@bookTicker";
            case DEPTH      -> lowerSymbol + "@depth";
        };
    }
}
