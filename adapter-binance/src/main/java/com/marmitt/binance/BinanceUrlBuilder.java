package com.marmitt.binance;

import com.marmitt.core.dto.common.CurrencyPair;
import com.marmitt.core.dto.websocket.request.StreamSubscriptionRequest;
import com.marmitt.core.ports.outbound.exchange.adapter.ExchangeUrlBuilderPort;

import java.util.List;
import java.util.stream.Collectors;

public class BinanceUrlBuilder implements ExchangeUrlBuilderPort {

    @Override
    public String buildConnectionUrl(StreamSubscriptionRequest parameters) {
        List<CurrencyPair> currencyPairs = parameters.getCurrencyPairs();
        
        if (currencyPairs.isEmpty()) {
            throw new IllegalArgumentException("At least one currency pair is required");
        }

        // Se é apenas um símbolo, usa single stream
        if (currencyPairs.size() == 1) {
            CurrencyPair pair = currencyPairs.getFirst();
            String binanceSymbol = buildStreamName(pair);
//            String stream = binanceSymbol.toLowerCase() + "@ticker";
            return Configuration.BASE_URL + Configuration.SINGLE_STREAM_PATH + "/" + binanceSymbol;
        }

        // Para múltiplos símbolos, usa combined stream
        List<String> streams = currencyPairs.stream()
                .map(this::buildStreamName)
//                .map(symbol -> symbol.toLowerCase() + "@ticker")
                .collect(Collectors.toList());
        
        String streamQuery = String.join("/", streams);
        return Configuration.BASE_URL + Configuration.COMBINED_STREAM_PATH + "?streams=" + streamQuery;
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