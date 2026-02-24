package com.marmitt.core.application.usecase.runner.processsignal;

import com.marmitt.core.domain.runner.StrategyRunner;
import com.marmitt.core.dto.strategy.StrategyOutputDto;
import com.marmitt.core.enums.ExecutionPolicy;
import com.marmitt.core.enums.TradingAction;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RunnerSignalPolicy {

    public boolean canProcessRunner(StrategyRunner runner, String exchangeId) {
        if (!runner.canAcceptSignals()) {
            log.debug("priceUpdate: skipping runner={} - canAcceptSignals=false (status={} reconciling={})",
                    runner.getId(), runner.getStatus(), runner.isReconciling());
            return false;
        }

        if (!runner.canReceiveMarketDataFrom(exchangeId)) {
            log.debug("priceUpdate: skipping runner={} - exchange={} not in allowedSources",
                    runner.getId(), exchangeId);
            return false;
        }

        return true;
    }

    public boolean canExecuteSignal(StrategyRunner runner,
                                    StrategyOutputDto signal,
                                    boolean hasOpenPositionOrInFlight) {
        if (!runner.canAcceptSignals()) {
            log.warn("processTradeSignal: runner={} cannot accept signals (status={} reconciling={})",
                    runner.getId(), runner.getStatus(), runner.isReconciling());
            return false;
        }

        if (signal.decision() == TradingAction.SHOULD_HOLD) {
            log.debug("processTradeSignal: HOLD signal discarded for runner={}", runner.getId());
            return false;
        }

        if (runner.getExecutionPolicy() == ExecutionPolicy.SINGLE
                && signal.decision() == TradingAction.SHOULD_BUY
                && hasOpenPositionOrInFlight) {
            log.info("processTradeSignal: BUY signal rejected by SINGLE policy - " +
                            "open position or in-flight orders exist for runner={}",
                    runner.getId());
            return false;
        }

        return true;
    }
}
