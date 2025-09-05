package com.marmitt.coinbase;

import com.marmitt.core.dto.configuration.CurrencyPair;
import com.marmitt.core.dto.configuration.WebSocketConnectionParameters;
import com.marmitt.core.enums.StreamType;
import com.marmitt.core.ports.outbound.ExchangeUrlBuilderPort;

import java.util.List;
import java.util.stream.Collectors;

public class CoinbaseUrlBuilder implements ExchangeUrlBuilderPort {

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

        // Converte currency pairs para formato Coinbase (BTC-USD)
        String symbolsParam = currencyPairs.stream()
                .map(this::formatCoinbaseSymbol)
                .collect(Collectors.joining(","));

        // Adiciona símbolo como query parameter para que o listener possa processar
        return CoinbaseConfiguration.BASE_URL + CoinbaseConfiguration.SINGLE_STREAM_PATH + 
               "?symbols=" + symbolsParam;
    }

    private String formatCoinbaseSymbol(CurrencyPair pair) {
        return pair.baseCurrency().toUpperCase() + "-" + pair.quoteCurrency().toUpperCase();
    }
}