package com.marmitt.strategy.impl.scenario;

import com.marmitt.core.dto.strategy.StrategyContextDto;
import com.marmitt.core.dto.strategy.StrategyInputDto;
import com.marmitt.core.dto.strategy.StrategyOutputDto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/**
 * Cenário 4 (T35): emite uma BUY cujo valor estimado ({@code quantity × price}) excede o capital
 * disponível, forçando o outcome {@code REJECTED_CAPITAL} na {@code CapitalReservationPolicy}.
 *
 * <p>Como a reserva é recusada antes do dispatch, nenhuma ordem vira {@code PENDING} nem lote —
 * a BUY não é barrada pela política SINGLE e o cenário se repete a cada tick. Usa preço de mercado
 * (sem {@code limitPrice}); só a quantidade importa aqui.
 */
public class OverAllocationRejectStrategy extends AbstractScenarioStrategy {

    private static final UUID STRATEGY_ID = UUID.fromString("b1000000-0000-4000-a000-000000000004");
    private static final String NAME = "OverAllocationRejectStrategy";
    private static final String VERSION = "1.0.0";

    public OverAllocationRejectStrategy() {
        this(ScenarioStrategyConfig.defaultConfig());
    }

    public OverAllocationRejectStrategy(ScenarioStrategyConfig config) {
        super(STRATEGY_ID, NAME, VERSION, config);
    }

    @Override
    public StrategyOutputDto executeStrategy(StrategyInputDto input, StrategyContextDto context) {
        BigDecimal quantity = overAllocatingQuantity(input.currentPrice(), context.availableCapital());
        return StrategyOutputDto.buy(NAME, CONFIDENCE, quantity,
                "cenario over-allocation-reject: BUY acima do capital para forcar REJECTED_CAPITAL");
    }

    /**
     * Quantidade cujo custo estimado supera o capital disponível: {@code (available / price) ×
     * factor}, com {@code factor > 1}. Quando não há capital/preço utilizável, cai para a
     * quantidade fixa do config (positiva) — a reserva de valor não-nulo contra saldo zero também
     * é recusada.
     */
    private BigDecimal overAllocatingQuantity(BigDecimal price, BigDecimal availableCapital) {
        if (price != null && price.signum() > 0 && availableCapital != null && availableCapital.signum() > 0) {
            BigDecimal quantity = availableCapital
                    .divide(price, 8, RoundingMode.HALF_UP)
                    .multiply(config.overAllocationFactor());
            if (quantity.signum() > 0) {
                return quantity;
            }
        }
        return config.quantity();
    }
}
