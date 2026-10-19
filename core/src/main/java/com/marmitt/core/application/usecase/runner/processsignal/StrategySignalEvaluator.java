package com.marmitt.core.application.usecase.runner.processsignal;

import com.marmitt.core.domain.runner.StrategyRunner;
import com.marmitt.core.dto.strategy.StrategyContextDto;
import com.marmitt.core.dto.strategy.StrategyInputDto;
import com.marmitt.core.dto.strategy.StrategyOutputDto;
import com.marmitt.core.enums.TradingAction;
import com.marmitt.core.exceptions.StrategyUnavailableException;
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
     * Resolve a estrategia ativa do runner pelo {@code strategyId} (T41: so por id — com duas versoes
     * de mesmo nome no ar, resolver por nome ficaria ambiguo).
     *
     * @return a estrategia habilitada, ou vazio se nao encontrada ou desabilitada —
     *         o chamador deve tratar ausencia como HOLD implicito
     */
    public Optional<TradingStrategy> resolveActiveStrategy(StrategyRunner runner) {
        Optional<TradingStrategy> strategy = strategyRepository.findById(runner.getStrategyId());

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
     * Executa a estrategia. Qualquer falha — retorno nulo, sinal invalido, excecao da estrategia
     * in-process ou estrategia remota indisponivel ({@link StrategyUnavailableException}) — vira
     * HOLD implicito com {@code unavailable = true}: o runner nao age neste tick e o monitoramento
     * distingue "estrategia decidiu esperar" de "estrategia nao decidiu" (T41).
     */
    public Evaluation evaluate(StrategyRunner runner,
                               TradingStrategy strategy,
                               StrategyInputDto input,
                               StrategyContextDto context) {
        try {
            StrategyOutputDto output = strategy.executeStrategy(input, context);
            if (output == null) {
                return unavailable(strategy, "strategy returned null");
            }
            return new Evaluation(output, false);
        } catch (StrategyUnavailableException e) {
            log.warn("priceUpdate: strategy unavailable runner={} strategy={} reason={} - {} - HOLD",
                    runner.getId(), strategy.getStrategyName(), e.reason(), e.getMessage());
            return unavailable(strategy, e.reason() + ": " + e.getMessage());
        } catch (RuntimeException e) {
            log.warn("priceUpdate: strategy failed runner={} strategy={} - {} - HOLD",
                    runner.getId(), strategy.getStrategyName(), e.toString());
            return unavailable(strategy, e.toString());
        }
    }

    private static Evaluation unavailable(TradingStrategy strategy, String reason) {
        return new Evaluation(StrategyOutputDto.hold(strategy.getStrategyName(), "Strategy unavailable: " + reason), true);
    }

    /**
     * @param unavailable a estrategia nao produziu decisao utilizavel; {@code output} e um HOLD implicito
     */
    record Evaluation(StrategyOutputDto output, boolean unavailable) {
    }

    /**
     * Retorna {@code true} se a decisao e HOLD, indicando que nenhuma acao deve ser tomada.
     * Centraliza a semantica de HOLD para evitar comparacoes de enum espalhadas no pipeline.
     */
    public boolean isHold(StrategyOutputDto output) {
        return output.decision() == TradingAction.SHOULD_HOLD;
    }
}
