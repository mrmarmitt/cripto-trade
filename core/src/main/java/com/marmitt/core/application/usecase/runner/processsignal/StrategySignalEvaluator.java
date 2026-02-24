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

@Slf4j
class StrategySignalEvaluator {

    private final StrategyRepositoryPort strategyRepository;

    public StrategySignalEvaluator(StrategyRepositoryPort strategyRepository) {
        this.strategyRepository = strategyRepository;
    }

    public StrategyOutputDto evaluate(StrategyRunner runner,
                                      StrategyInputDto input,
                                      PortfolioContextDto context) {
        Optional<TradingStrategy> strategy = strategyRepository.findById(runner.getStrategyId())
                .or(() -> strategyRepository.findByName(runner.getStrategyName()));

        if (strategy.isEmpty()) {
            log.warn("priceUpdate: strategy not found for runner={} strategyId={} strategyName={}",
                    runner.getId(), runner.getStrategyId(), runner.getStrategyName());
            return StrategyOutputDto.hold(runner.getStrategyName(), "Strategy not found");
        }

        if (!strategy.get().isEnabled()) {
            log.debug("priceUpdate: strategy disabled for runner={}", runner.getId());
            return StrategyOutputDto.hold(runner.getStrategyName(), "Strategy disabled");
        }

        StrategyOutputDto output = strategy.get().executeStrategy(input, context);
        if (output == null) {
            return StrategyOutputDto.hold(runner.getStrategyName(),
                    "Execution of strategy returned null, SHOULD_HOLD by default.");
        }
        return output;
    }

    public boolean isHold(StrategyOutputDto output) {
        return output.decision() == TradingAction.SHOULD_HOLD;
    }
}
