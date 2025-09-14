package com.marmitt.listener;

import com.marmitt.core.domain.data.MarketData;
import com.marmitt.core.ports.outbound.listener.PriceUpdateListener;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementação exemplo de PriceUpdateListener para monitoramento de preços.
 * Esta implementação demonstra como receber e processar atualizações de preços,
 * incluindo detecção de mudanças significativas.
 */
@Slf4j
public class MarketDataPriceUpdateListener implements PriceUpdateListener {
    
    // Cache de último preço para detectar mudanças significativas
    private final Map<String, BigDecimal> lastPriceCache = new ConcurrentHashMap<>();
    
    // Threshold para mudanças significativas (1% por padrão)
    private static final BigDecimal SIGNIFICANT_CHANGE_THRESHOLD = new BigDecimal("0.01");
    
    @Override
    public void onPriceUpdate(MarketData marketData) {
        String symbol = marketData.symbol().value();
        BigDecimal currentPrice = marketData.price();
        
        log.debug("Price Update Received - Symbol: {}, Price: {}, Volume: {}, Timestamp: {}", 
                symbol, 
                currentPrice,
                marketData.volume(),
                marketData.timestamp());
        
        // Verifica se há uma mudança significativa no preço
        BigDecimal lastPrice = lastPriceCache.get(symbol);
        if (lastPrice != null) {
            BigDecimal priceChange = currentPrice.subtract(lastPrice);
            BigDecimal percentageChange = priceChange.divide(lastPrice, 4, BigDecimal.ROUND_HALF_UP).abs();
            
            if (percentageChange.compareTo(SIGNIFICANT_CHANGE_THRESHOLD) > 0) {
                log.info("Significant price change detected for {}: {} -> {} ({}%)", 
                        symbol, 
                        lastPrice, 
                        currentPrice,
                        percentageChange.multiply(new BigDecimal("100")).setScale(2, BigDecimal.ROUND_HALF_UP));
                
                // Aqui você pode implementar lógica para mudanças significativas
                handleSignificantPriceChange(marketData, lastPrice, percentageChange);
            }
        }
        
        // Atualiza o cache com o novo preço
        lastPriceCache.put(symbol, currentPrice);
        
        // Aqui você pode implementar lógica adicional como:
        // - Análise técnica
        // - Triggers de estratégias
        // - Atualização de portfólio
        // - Notificações para usuários
    }
    
    private void handleSignificantPriceChange(MarketData marketData, BigDecimal lastPrice, BigDecimal percentageChange) {
        // Implementação exemplo para mudanças significativas
        String direction = marketData.price().compareTo(lastPrice) > 0 ? "UP" : "DOWN";
        
        log.info("Price movement alert: {} moved {} by {}%", 
                marketData.symbol().value(), 
                direction, 
                percentageChange.multiply(new BigDecimal("100")).setScale(2, BigDecimal.ROUND_HALF_UP));
        
        // Aqui você pode:
        // 1. Disparar notificações
        // 2. Executar estratégias automáticas
        // 3. Atualizar dashboards em tempo real
        // 4. Salvar dados para análise posterior
    }
}