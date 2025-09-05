package com.marmitt.coinbase;

import com.marmitt.core.dto.configuration.WebSocketConnectionParameters;
import com.marmitt.core.ports.outbound.ExchangeUrlBuilderPort;

import java.util.List;
import java.util.stream.Collectors;

public class CoinbaseUrlBuilder implements ExchangeUrlBuilderPort {

    @Override
    public String buildConnectionUrl(WebSocketConnectionParameters parameters) {
        String baseCurrency = (String) parameters.getParameterValue("baseCurrency");
        String quoteCurrency = (String) parameters.getParameterValue("quoteCurrency");
        
        if (baseCurrency == null || quoteCurrency == null) {
            throw new IllegalArgumentException("baseCurrency and quoteCurrency are required for TICKER stream");
        }

        // Cria símbolo no formato Coinbase (BTC-USD)
        String coinbaseSymbol = baseCurrency.toUpperCase() + "-" + quoteCurrency.toUpperCase();

        // Adiciona símbolo como query parameter para que o listener possa processar
        return CoinbaseConfiguration.BASE_URL + CoinbaseConfiguration.SINGLE_STREAM_PATH + 
               "?symbols=" + coinbaseSymbol;
    }
}