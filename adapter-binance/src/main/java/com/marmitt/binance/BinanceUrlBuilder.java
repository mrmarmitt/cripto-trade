package com.marmitt.binance;

import com.marmitt.core.dto.common.CurrencyPair;
import com.marmitt.core.dto.websocket.request.StreamSubscriptionRequest;
import com.marmitt.core.ports.outbound.exchange.adapter.ExchangeUrlBuilderPort;

import java.util.List;
import java.util.stream.Collectors;

public class BinanceUrlBuilder implements ExchangeUrlBuilderPort {

    private final Configuration configuration;

    public BinanceUrlBuilder(Configuration configuration) {
        this.configuration = configuration;
    }

    @Override
    public String buildConnectionUrl(StreamSubscriptionRequest parameters) {
        List<CurrencyPair> currencyPairs = parameters.getCurrencyPairs();
        if (currencyPairs.isEmpty()) {
            throw new IllegalArgumentException("At least one currency pair is required");
        }

        if (currencyPairs.size() == 1) {
            CurrencyPair pair = currencyPairs.getFirst();
            String binanceSymbol = buildStreamName(pair);
            return configuration.getWebSocketBaseUrl() + Configuration.SINGLE_STREAM_PATH + "/" + binanceSymbol;
        }

        List<String> streams = currencyPairs.stream()
                .map(this::buildStreamName)
                .collect(Collectors.toList());

        String streamQuery = String.join("/", streams);
        return configuration.getWebSocketBaseUrl() + Configuration.COMBINED_STREAM_PATH + "?streams=" + streamQuery;
    }

    public String getWebSocketBaseUrl() {
        return configuration.getWebSocketBaseUrl();
    }

    public String getRestBaseUrl() {
        return configuration.getRestBaseUrl();
    }

    private String buildStreamName(CurrencyPair currencyPair) {
        String lowerSymbol = (currencyPair.baseCurrency() + currencyPair.quoteCurrency()).toLowerCase();
        return switch (currencyPair.streamType()) {
            case TICKER -> lowerSymbol + "@ticker";
            case TRADE -> lowerSymbol + "@trade";
            case BOOK_TICKER -> lowerSymbol + "@bookTicker";
            case DEPTH -> lowerSymbol + "@depth";
        };
    }
}
