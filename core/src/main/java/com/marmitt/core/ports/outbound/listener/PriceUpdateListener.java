package com.marmitt.core.ports.outbound.listener;

import com.marmitt.core.domain.data.MarketData;

/**
 * Interface para receber notificações de atualizações de preços.
 * Implementações desta interface serão notificadas quando houver
 * mudanças nos preços de mercado dos símbolos monitorados.
 */
public interface PriceUpdateListener {
    
    /**
     * Método chamado quando há uma atualização de preço.
     * 
     * @param marketData dados atualizados de mercado
     */
    void onPriceUpdate(MarketData marketData);
}