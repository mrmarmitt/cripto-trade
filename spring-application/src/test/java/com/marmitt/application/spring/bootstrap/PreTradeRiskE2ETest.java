package com.marmitt.application.spring.bootstrap;

import com.marmitt.core.dto.strategy.StrategyContextDto;
import com.marmitt.core.dto.strategy.StrategyInputDto;
import com.marmitt.core.dto.strategy.StrategyOutputDto;
import com.marmitt.core.ports.outbound.strategy.TradingStrategy;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T50 — sanidade de preco contra o MOCK: uma BUY com o {@code limitPrice} uma casa decimal fora (10x
 * abaixo do tick) e descartada antes de persistir, com o desfecho {@code REJECTED_PRICE_DEVIATION}.
 */
class PreTradeRiskE2ETest extends ScenarioStrategyE2ETestSupport {

    @Test
    void buyWithAMisplacedDecimalIsDiscardedBeforeAnyEffect() {
        UUID runnerId = activateScenario(new MisplacedDecimalStrategy());
        UUID portfolioId = portfolioIdOf(runnerId);

        driveTicks(4);
        sleep(QUIET_WINDOW.toMillis());

        assertEquals(0, transactionCount(runnerId), "Nenhuma transacao pode ser criada" + System.lineSeparator()
                + stateDump(runnerId));
        assertTrue(signalEvaluatedCount(runnerId, "REJECTED_PRICE_DEVIATION") >= 1.0);
        assertEquals(0, balance(portfolioId).reserved().signum(), "Nada reservado");
        assertEquals(0, INITIAL_CAPITAL.compareTo(balance(portfolioId).available()));
    }

    /** Sempre pede BUY a um decimo do preco do tick (erro de casa decimal). */
    private static final class MisplacedDecimalStrategy implements TradingStrategy {

        private final UUID id = UUID.randomUUID();
        private boolean enabled = true;

        @Override
        public StrategyOutputDto executeStrategy(StrategyInputDto input, StrategyContextDto context) {
            return StrategyOutputDto.buyAt(getStrategyName(), BigDecimal.ONE, new BigDecimal("0.001"),
                    input.currentPrice().divide(BigDecimal.TEN, 2, RoundingMode.HALF_UP), "t50: casa decimal trocada");
        }

        @Override
        public UUID getStrategyId() {
            return id;
        }

        @Override
        public String getStrategyName() {
            return "T50MisplacedDecimal";
        }

        @Override
        public String getStrategyVersion() {
            return "1.0.0";
        }

        @Override
        public boolean isEnabled() {
            return enabled;
        }

        @Override
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
