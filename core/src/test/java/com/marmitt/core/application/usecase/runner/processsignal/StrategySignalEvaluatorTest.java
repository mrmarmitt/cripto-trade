package com.marmitt.core.application.usecase.runner.processsignal;

import com.marmitt.core.domain.runner.StrategyRunner;
import com.marmitt.core.dto.strategy.StrategyContextDto;
import com.marmitt.core.dto.strategy.StrategyInputDto;
import com.marmitt.core.dto.strategy.StrategyOutputDto;
import com.marmitt.core.enums.TradingAction;
import com.marmitt.core.exceptions.StrategyUnavailableException;
import com.marmitt.core.ports.outbound.repository.StrategyRepositoryPort;
import com.marmitt.core.ports.outbound.strategy.TradingStrategy;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * T41: qualquer falha da estrategia vira HOLD com {@code unavailable}; resolucao so por id.
 */
class StrategySignalEvaluatorTest {

    private final StrategyRepositoryPort repository = mock(StrategyRepositoryPort.class);
    private final StrategySignalEvaluator evaluator = new StrategySignalEvaluator(repository);
    private final StrategyRunner runner = mock(StrategyRunner.class);
    private final TradingStrategy strategy = mock(TradingStrategy.class);

    @Test
    void validDecisionIsAvailable() {
        when(strategy.executeStrategy(any(), any())).thenReturn(StrategyOutputDto.buy("s", BigDecimal.ONE, BigDecimal.ONE, "x"));

        StrategySignalEvaluator.Evaluation evaluation = evaluate();

        assertFalse(evaluation.unavailable());
        assertEquals(TradingAction.SHOULD_BUY, evaluation.output().decision());
    }

    @Test
    void remoteUnavailableNullAndArbitraryExceptionsBecomeUnavailableHold() {
        when(strategy.executeStrategy(any(), any()))
                .thenThrow(new StrategyUnavailableException(StrategyUnavailableException.Reason.UNREACHABLE, "timeout"))
                .thenReturn(null)
                .thenThrow(new IllegalStateException("bug in strategy"))
                .thenThrow(new IllegalArgumentException("invalid signal"));

        for (int i = 0; i < 4; i++) {
            StrategySignalEvaluator.Evaluation evaluation = evaluate();
            assertTrue(evaluation.unavailable());
            assertEquals(TradingAction.SHOULD_HOLD, evaluation.output().decision());
        }
    }

    @Test
    void strategyIsResolvedOnlyById() {
        UUID strategyId = UUID.randomUUID();
        when(runner.getStrategyId()).thenReturn(strategyId);
        when(repository.findById(strategyId)).thenReturn(Optional.empty());

        assertTrue(evaluator.resolveActiveStrategy(runner).isEmpty(),
                "Sem fallback por nome: id desconhecido = estrategia nao encontrada");
    }

    private StrategySignalEvaluator.Evaluation evaluate() {
        when(strategy.getStrategyName()).thenReturn("s");
        return evaluator.evaluate(runner, strategy, mock(StrategyInputDto.class), mock(StrategyContextDto.class));
    }
}
