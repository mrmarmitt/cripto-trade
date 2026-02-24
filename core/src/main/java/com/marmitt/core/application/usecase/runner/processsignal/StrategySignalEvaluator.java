package com.marmitt.core.application.usecase.runner.processsignal;

import com.marmitt.core.domain.runner.StrategyRunner;
import com.marmitt.core.dto.strategy.PortfolioContextDto;
import com.marmitt.core.dto.strategy.StrategyInputDto;
import com.marmitt.core.dto.strategy.StrategyOutputDto;
import com.marmitt.core.enums.TradingAction;
import com.marmitt.core.ports.outbound.repository.StrategyRepositoryPort;
import com.marmitt.core.ports.outbound.strategy.TradingStrategy;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

/**
 * Resolve e executa a estrategia associada ao runner.
 * Converte ausencia/estrategia desabilitada em decisao HOLD.
 */
@Slf4j
class StrategySignalEvaluator {

    private final StrategyRepositoryPort strategyRepository;

    public StrategySignalEvaluator(StrategyRepositoryPort strategyRepository) {
        this.strategyRepository = strategyRepository;
    }

    /**
     * Resolve a estrategia do runner e valida se esta habilitada.
     * Retorna vazio quando nao encontrada ou desabilitada.
     */
    public Optional<TradingStrategy> resolveActiveStrategy(StrategyRunner runner) {
        Optional<TradingStrategy> strategy = strategyRepository.findById(runner.getStrategyId())
                .or(() -> strategyRepository.findByName(runner.getStrategyName()));

        if (strategy.isEmpty()) {
            log.warn("priceUpdate: strategy not found for runner={} strategyId={} strategyName={}",
                    runner.getId(), runner.getStrategyId(), runner.getStrategyName());
            return Optional.empty();
        }

        if (!strategy.get().isEnabled()) {
            log.debug("priceUpdate: strategy disabled for runner={}", runner.getId());
            return Optional.empty();
        }

        return strategy;
    }

    /**
     * Avalia o sinal da estrategia para o contexto atual do runner.
     */
    public StrategyOutputDto evaluate(StrategyRunner runner,
                                      TradingStrategy strategy,
                                      StrategyInputDto input,
                                      PortfolioContextDto context) {
        StrategyOutputDto output = strategy.executeStrategy(input, context);
        if (output == null) {
            return StrategyOutputDto.hold(runner.getStrategyName(),
                    "Execution of strategy returned null, SHOULD_HOLD by default.");
        }
        return output;
    }

    /**
     * Helper de leitura para simplificar o fluxo no use case.
     */
    public boolean isHold(StrategyOutputDto output) {
        return output.decision() == TradingAction.SHOULD_HOLD;
    }
}
