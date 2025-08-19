package com.marmitt.ctrade.domain.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MarketData {
    private Map<TradingPair, BigDecimal> currentPrices;
    private Map<TradingPair, BigDecimal> volumes24h;
    private LocalDateTime timestamp;

    public BigDecimal getPriceFor(TradingPair pair) {
        return currentPrices.get(pair);
    }

    public BigDecimal getVolumeFor(TradingPair pair) {
        return volumes24h.get(pair);
    }

    public boolean hasPriceFor(TradingPair pair) {
        return currentPrices.containsKey(pair) && currentPrices.get(pair) != null;
    }
}