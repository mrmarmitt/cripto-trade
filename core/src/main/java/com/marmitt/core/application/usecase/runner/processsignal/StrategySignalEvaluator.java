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
 * Resolve a estrategia configurada no runner e produz uma decisao de trade.
 *
 * <p>A resolucao usa uma cadeia de fallback: primeiro busca pelo {@code strategyId},
 * depois pelo {@code strategyName}. Isso permite que a estrategia seja referenciada
 * de forma robusta mesmo apos recriacao (novo ID, mesmo nome).
 *
 * <p>Qualquer resultado invalido (estrategia nao encontrada, desabilitada ou retorno
 * nulo) e convertido silenciosamente em HOLD. O runner nao e penalizado — ele simplesmente
 * nao age naquele tick. Isso e preferivel a lancar excecao e interromper os demais runners.
 */
@Slf4j
class StrategySignalEvaluator {

    private final StrategyRepositoryPort strategyRepository;

    public StrategySignalEvaluator(StrategyRepositoryPort strategyRepository) {
        this.strategyRepository = strategyRepository;
    }

    /**
     * Resolve a estrategia ativa do runner, tentando primeiro por ID e depois por nome.
     *
     * @return a estrategia habilitada, ou vazio se nao encontrada ou desabilitada —
     *         o chamador deve tratar ausencia como HOLD implicito
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
     * Executa a estrategia e retorna a decisao de trade.
     * Retorno nulo da estrategia e normalizado para HOLD — estrategias mal implementadas
     * nao devem causar NullPointerException no pipeline principal.
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
     * Retorna {@code true} se a decisao e HOLD, indicando que nenhuma acao deve ser tomada.
     * Centraliza a semantica de HOLD para evitar comparacoes de enum espalhadas no pipeline.
     */
    public boolean isHold(StrategyOutputDto output) {
        return output.decision() == TradingAction.SHOULD_HOLD;
    }
}
