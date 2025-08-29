package com.marmitt.ctrade.infrastructure.exchange.binance;

import com.marmitt.ctrade.config.TradingProperties;
import com.marmitt.ctrade.domain.port.TradingPairProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Implementação específica para Binance de prover trading pairs.
 * 
 * Gera streams no formato Binance: "btcusdt@ticker/ethusdt@ticker"
 * Permite configurar os trading pairs via application.yml.
 */
@Slf4j
public class BinanceTradingPairProvider implements TradingPairProvider {

    private final TradingProperties tradingProperties;

    private final String STREAM_FORMAT = "ticker";

    public BinanceTradingPairProvider(TradingProperties tradingProperties) {
        this.tradingProperties = tradingProperties;
    }

    @Override
    public List<String> getActiveTradingPairs() {
        if (tradingProperties.pair().active().isEmpty()) {
            throw new RuntimeException("No active trading pairs configured.");
        }
        
        return new ArrayList<>(tradingProperties.pair().active());
    }
    
    @Override
    public boolean isActiveTradingPair(String symbol) {
        if (symbol == null) {
            return false;
        }
        
        String normalizedSymbol = symbol.toUpperCase();
        return getActiveTradingPairs().contains(normalizedSymbol);
    }
    
    @Override
    public String getFormattedStreamList() {
        List<String> pairs = getActiveTradingPairs();
        
        // Formato específico da Binance: "btcusdt@ticker/ethusdt@ticker"
        String formatted = pairs.stream()
                .map(pair -> StringUtils.remove("/", pair))
                .map(pair -> pair.toLowerCase() + "@" + STREAM_FORMAT)
                .collect(Collectors.joining("/"));
        
        log.debug("Binance formatted stream list: {}", formatted);
        return formatted;
    }
}