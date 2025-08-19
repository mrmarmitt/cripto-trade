package com.marmitt.ctrade.infrastructure.config;

import com.marmitt.ctrade.domain.port.TradingPairProvider;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Implementação baseada em configuração para prover trading pairs.
 * 
 * Permite configurar os trading pairs via application.yml e facilita
 * migração futura para implementação baseada em banco de dados.
 */
@Getter
@Component
@ConfigurationProperties(prefix = "trading.pairs")
@RequiredArgsConstructor
@Slf4j
public class ConfigurationBasedTradingPairProvider implements TradingPairProvider {
    
    /**
     * Lista de trading pairs configurados via application.yml
     * Exemplo: ["BTCUSDT", "ETHUSDT", "ADAUSDT"]
     */
    private List<String> active = new ArrayList<>();
    
    /**
     * Formato do stream para cada trading pair
     * Exemplo: "ticker" resulta em "btcusdt@ticker"
     */
    private String streamFormat = "ticker";
    
    @Override
    public List<String> getActiveTradingPairs() {
        if (active.isEmpty()) {
            log.warn("No active trading pairs configured, using defaults");
            return getDefaultTradingPairs();
        }
        
        log.debug("Active trading pairs: {}", active);
        return new ArrayList<>(active);
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
        
        String formatted = pairs.stream()
                .map(pair -> pair.toLowerCase() + "@" + streamFormat)
                .collect(Collectors.joining("/"));
        
        log.debug("Formatted stream list: {}", formatted);
        return formatted;
    }
    
    /**
     * Configuração padrão caso nenhuma seja especificada
     */
    private List<String> getDefaultTradingPairs() {
        return List.of("BTCUSDT", "BTCUSDC", "USDCUSDT");
    }
    
    // Getters e Setters para @ConfigurationProperties

    public void setActive(List<String> active) {
        this.active = active;
        log.info("Updated active trading pairs: {}", active);
    }

    public void setStreamFormat(String streamFormat) {
        this.streamFormat = streamFormat;
        log.info("Updated stream format: {}", streamFormat);
    }
}