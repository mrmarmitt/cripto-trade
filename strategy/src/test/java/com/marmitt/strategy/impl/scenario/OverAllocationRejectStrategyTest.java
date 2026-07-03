package com.marmitt.strategy.impl.scenario;

import com.marmitt.core.dto.strategy.StrategyOutputDto;
import com.marmitt.core.enums.TradingAction;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static com.marmitt.strategy.impl.scenario.ScenarioTestFixtures.context;
import static com.marmitt.strategy.impl.scenario.ScenarioTestFixtures.input;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OverAllocationRejectStrategyTest {

    private static final BigDecimal MARKET = new BigDecimal("65000");

    private final OverAllocationRejectStrategy strategy = new OverAllocationRejectStrategy();

    @Test
    void emitsBuyWhoseCostExceedsAvailableCapital() {
        BigDecimal available = new BigDecimal("1000");
        StrategyOutputDto out = strategy.executeStrategy(
                input(MARKET), context(available, List.of(), List.of()));

        assertEquals(TradingAction.SHOULD_BUY, out.decision());
        BigDecimal estimatedCost = out.quantity().multiply(MARKET);
        assertTrue(estimatedCost.compareTo(available) > 0,
                "custo estimado (" + estimatedCost + ") deve exceder o capital disponivel (" + available + ")");
    }

    @Test
    void flooredToFixedQuantityWhenBalanceTooSmall() {
        // available=10 com price=65000 daria (10/65000)*2 ≈ 0.0003077, abaixo do fixo 0.001 e
        // vulneravel ao floor de stepSize (viraria 0). Deve usar o fixo como piso e ainda over-alocar.
        BigDecimal available = new BigDecimal("10");
        StrategyOutputDto out = strategy.executeStrategy(
                input(MARKET), context(available, List.of(), List.of()));

        assertEquals(TradingAction.SHOULD_BUY, out.decision());
        assertTrue(out.quantity().compareTo(new BigDecimal("0.001")) >= 0,
                "quantidade (" + out.quantity() + ") nao pode ficar abaixo do fixo do config (0.001)");
        BigDecimal estimatedCost = out.quantity().multiply(MARKET);
        assertTrue(estimatedCost.compareTo(available) > 0,
                "custo estimado (" + estimatedCost + ") deve exceder o capital disponivel (" + available + ")");
    }

    @Test
    void fallsBackToPositiveQuantityWhenNoCapital() {
        StrategyOutputDto out = strategy.executeStrategy(
                input(MARKET), context(BigDecimal.ZERO, List.of(), List.of()));

        assertEquals(TradingAction.SHOULD_BUY, out.decision());
        assertTrue(out.quantity().compareTo(BigDecimal.ZERO) > 0, "quantidade deve ser positiva");
    }

    @Test
    void stopsAfterMaxCyclesReached() {
        OverAllocationRejectStrategy bounded = new OverAllocationRejectStrategy(ScenarioStrategyConfig.boundedConfig(2));
        BigDecimal available = new BigDecimal("1000");

        assertEquals(TradingAction.SHOULD_BUY,
                bounded.executeStrategy(input(MARKET), context(available, List.of(), List.of())).decision());
        assertEquals(TradingAction.SHOULD_BUY,
                bounded.executeStrategy(input(MARKET), context(available, List.of(), List.of())).decision());
        // teto de 2 atingido -> HOLD, sem mais tentativas de reserva
        assertEquals(TradingAction.SHOULD_HOLD,
                bounded.executeStrategy(input(MARKET), context(available, List.of(), List.of())).decision());
    }
}
