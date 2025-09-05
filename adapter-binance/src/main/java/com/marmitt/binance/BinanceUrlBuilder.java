package com.marmitt.binance;

import com.marmitt.core.dto.configuration.CurrencyPair;
import com.marmitt.core.dto.configuration.WebSocketConnectionParameters;
import com.marmitt.core.enums.StreamType;
import com.marmitt.core.ports.outbound.ExchangeUrlBuilderPort;

import java.util.List;
import java.util.stream.Collectors;

public class BinanceUrlBuilder implements ExchangeUrlBuilderPort {

    @Override
    public String buildConnectionUrl(WebSocketConnectionParameters parameters) {
        List<CurrencyPair> currencyPairs = parameters.getCurrencyPairs();
        
        if (currencyPairs.isEmpty()) {
            throw new IllegalArgumentException("At least one currency pair is required");
        }

        List<StreamType> streamTypes = parameters.streamType();
        if (streamTypes.isEmpty()) {
            throw new IllegalArgumentException("At least one stream type is required");
        }

        // Para agora, implementa apenas TICKER
        StreamType streamType = streamTypes.get(0);
        if (streamType != StreamType.TICKER) {
            throw new UnsupportedOperationException("Only TICKER stream type is currently supported");
        }

        // Se é apenas um símbolo, usa single stream
        if (currencyPairs.size() == 1) {
            CurrencyPair pair = currencyPairs.get(0);
            String binanceSymbol = formatBinanceSymbol(pair);
            String stream = binanceSymbol.toLowerCase() + "@ticker";
            return BinanceConfiguration.BASE_URL + BinanceConfiguration.SINGLE_STREAM_PATH + "/" + stream;
        }

        // Para múltiplos símbolos, usa combined stream
        List<String> streams = currencyPairs.stream()
                .map(this::formatBinanceSymbol)
                .map(symbol -> symbol.toLowerCase() + "@ticker")
                .collect(Collectors.toList());
        
        String streamQuery = String.join("/", streams);
        return BinanceConfiguration.BASE_URL + BinanceConfiguration.COMBINED_STREAM_PATH + "?streams=" + streamQuery;
    }

    private String formatBinanceSymbol(CurrencyPair pair) {
        return pair.baseCurrency().toUpperCase() + pair.quoteCurrency().toUpperCase();
    }
}