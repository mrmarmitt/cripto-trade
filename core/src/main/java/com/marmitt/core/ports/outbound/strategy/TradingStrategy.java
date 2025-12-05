package com.marmitt.core.ports.outbound.strategy;

import com.marmitt.core.domain.strategy.StrategyInput;
import com.marmitt.core.domain.strategy.StrategyOutput;
import com.marmitt.core.domain.strategy.PortfolioContext;

import java.util.UUID;

public interface TradingStrategy {
    
    UUID getStrategyId();
    
    /**
     * Executa a estratégia com contexto completo do portfolio
     * 
     * @param inputData Dados de mercado (preços, volume, etc)
     * @param portfolioContext Contexto do portfolio (capital, posições, limites)
     * @return Decisão da estratégia com quantity absoluta calculada
     */
    StrategyOutput executeStrategy(StrategyInput inputData, PortfolioContext portfolioContext);

    String getStrategyName();

    String getStrategyVersion();

    boolean isEnabled();

    void setEnabled(boolean enabled);
}