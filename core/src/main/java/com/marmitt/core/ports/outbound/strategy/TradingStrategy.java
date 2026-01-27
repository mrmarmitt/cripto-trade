package com.marmitt.core.ports.outbound.strategy;

import com.marmitt.core.dto.strategy.StrategyInputDto;
import com.marmitt.core.dto.strategy.StrategyOutputDto;
import com.marmitt.core.dto.strategy.PortfolioContextDto;

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
    StrategyOutputDto executeStrategy(StrategyInputDto inputData, PortfolioContextDto portfolioContext);

    String getStrategyName();

    String getStrategyVersion();

    boolean isEnabled();

    void setEnabled(boolean enabled);
}