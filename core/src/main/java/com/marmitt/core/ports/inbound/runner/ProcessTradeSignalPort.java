package com.marmitt.core.ports.inbound.runner;

import com.marmitt.core.domain.runner.StrategyRunner;
import com.marmitt.core.dto.strategy.StrategyOutputDto;

import java.math.BigDecimal;

/**
 * Porta de entrada do fluxo ProcessTradeSignal.
 * <p>
 * Implementada por {@code ProcessTradeSignalHandler} (spring-application),
 * que gerencia os limites {@code @Transactional}.
 * Chamada pelo {@code RunnerUseCase} após execução da estratégia.
 *
 * @see <a href="docs/IMPLEMENTATION_GUIDE.md">IG Seção 6.2.1</a>
 */
public interface ProcessTradeSignalPort {

    /**
     * Processa um sinal de trading produzido pela estratégia.
     *
     * @param runner       runner que recebeu o sinal
     * @param signal       decisão da estratégia (BUY, SELL ou HOLD)
     * @param currentPrice preço de mercado corrente
     */
    void handle(StrategyRunner runner, StrategyOutputDto signal, BigDecimal currentPrice);
}
