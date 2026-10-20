package com.marmitt.application.spring.config.core;

import com.marmitt.core.enums.StreamType;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Abertura automática de conexões e assinaturas de market data pela demanda dos runners (T56).
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "ctrade.market-data.auto-feed")
public class MarketDataFeedProperties {

    /** Desligado: conexões e assinaturas só pelos endpoints manuais. */
    private boolean enabled = true;
    /** Intervalo do ciclo que confere a demanda contra o que está chegando. */
    private long intervalMs = 30_000L;
    /** Tipo de stream assinado quando a exchange não tem um próprio em {@link #streamTypes}. */
    private StreamType defaultStreamType = StreamType.TICKER;
    /** Tipo de stream por exchange (ex.: {@code BINANCE: TICKER}). */
    private Map<String, StreamType> streamTypes = new HashMap<>();

    public StreamType streamTypeFor(String exchange) {
        return streamTypes.entrySet().stream()
                .filter(entry -> entry.getKey().toUpperCase(Locale.ROOT).equals(exchange.toUpperCase(Locale.ROOT)))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(defaultStreamType);
    }
}
