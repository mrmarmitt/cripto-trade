package com.marmitt.core.application.listener;

import com.marmitt.core.dto.websocket.data.MarketDataDto;
import com.marmitt.core.ports.outbound.listener.PriceUpdateListener;
import com.marmitt.core.ports.outbound.marketdata.LatestPricePort;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Registra o último preço de cada símbolo no {@link LatestPricePort} (T43) e loga variações
 * significativas entre ticks consecutivos.
 */
@Slf4j
public class MarketDataPriceUpdateListener implements PriceUpdateListener {

    // Threshold para mudanças significativas (1% por padrão)
    private static final BigDecimal SIGNIFICANT_CHANGE_THRESHOLD = new BigDecimal("0.01");

    private final LatestPricePort latestPrices;

    public MarketDataPriceUpdateListener(LatestPricePort latestPrices) {
        this.latestPrices = latestPrices;
    }

    @Override
    public void onPriceUpdate(MarketDataDto marketDataDto) {
        String symbol = marketDataDto.symbol().value();
        BigDecimal currentPrice = marketDataDto.price();

        log.debug("Price Update Received - Symbol: {}, Price: {}, Volume: {}, Timestamp: {}",
                symbol, currentPrice, marketDataDto.volume(), marketDataDto.timestamp());

        latestPrices.find(symbol).ifPresent(lastPrice -> logSignificantChange(symbol, lastPrice, currentPrice));
        latestPrices.record(symbol, currentPrice);
    }

    private void logSignificantChange(String symbol, BigDecimal lastPrice, BigDecimal currentPrice) {
        BigDecimal percentageChange = currentPrice.subtract(lastPrice)
                .divide(lastPrice, 4, RoundingMode.HALF_UP).abs();
        if (percentageChange.compareTo(SIGNIFICANT_CHANGE_THRESHOLD) > 0) {
            log.info("Significant price change detected for {}: {} -> {} ({}%)",
                    symbol, lastPrice, currentPrice,
                    percentageChange.multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP));
        }
    }
}
