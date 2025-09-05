package com.marmitt.binance;

import com.marmitt.core.dto.configuration.WebSocketConnectionParameters;
import com.marmitt.core.ports.outbound.ExchangeUrlBuilderPort;

import java.util.List;
import java.util.stream.Collectors;

public class BinanceUrlBuilder implements ExchangeUrlBuilderPort {

     @Override
    public String buildConnectionUrl(WebSocketConnectionParameters parameters) {
        String baseCurrency = (String) parameters.getParameterValue("baseCurrency");
        String quoteCurrency = (String) parameters.getParameterValue("quoteCurrency");
        
        if (baseCurrency == null || quoteCurrency == null) {
            throw new IllegalArgumentException("baseCurrency and quoteCurrency are required for TICKER stream");
        }

        // Cria símbolo no formato Binance (BTCUSDT)
        String binanceSymbol = baseCurrency.toUpperCase() + quoteCurrency.toUpperCase();
        String tickerStream = binanceSymbol.toLowerCase() + "@ticker";

        // Para um único símbolo, usa single stream path
        return BinanceConfiguration.BASE_URL + BinanceConfiguration.SINGLE_STREAM_PATH + "/" + tickerStream;
    }
}