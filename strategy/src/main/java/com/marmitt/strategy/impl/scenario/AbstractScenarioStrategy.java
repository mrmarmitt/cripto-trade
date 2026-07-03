package com.marmitt.strategy.impl.scenario;

import com.marmitt.core.dto.strategy.PendingOrderDto;
import com.marmitt.core.dto.strategy.StrategyContextDto;
import com.marmitt.core.enums.TradingAction;
import com.marmitt.core.ports.outbound.strategy.TradingStrategy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import java.util.UUID;

/**
 * Base das estratégias de cenário determinísticas (T35). Concentra identidade (id/nome/versão),
 * o estado {@code enabled} e helpers de preço/estado; cada subclasse implementa apenas a decisão
 * do seu cenário.
 *
 * <p>São puras decisões — sem infra, rede ou schema de exchange — como qualquer estratégia do
 * módulo. Ficam registradas apenas quando explicitamente habilitadas (flag no spring-application).
 */
abstract class AbstractScenarioStrategy implements TradingStrategy {

    protected static final BigDecimal CONFIDENCE = BigDecimal.ONE;
    private static final int PRICE_SCALE = 8;

    private final UUID strategyId;
    private final String strategyName;
    private final String strategyVersion;
    protected final ScenarioStrategyConfig config;
    private boolean enabled = true;

    protected AbstractScenarioStrategy(UUID strategyId, String strategyName,
                                       String strategyVersion, ScenarioStrategyConfig config) {
        this.strategyId = strategyId;
        this.strategyName = strategyName;
        this.strategyVersion = strategyVersion;
        this.config = config;
    }

    @Override
    public UUID getStrategyId() {
        return strategyId;
    }

    @Override
    public String getStrategyName() {
        return strategyName;
    }

    @Override
    public String getStrategyVersion() {
        return strategyVersion;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /** Primeira ordem em trânsito do lado informado (BUY/SELL), se houver. */
    protected Optional<PendingOrderDto> firstPendingOfType(StrategyContextDto context, TradingAction type) {
        return context.pendingOrders().stream()
                .filter(pending -> pending.type() == type)
                .findFirst();
    }

    /** Preço abaixo do mercado por uma fração (ex.: descansar um BUY ou tornar um SELL marketable). */
    protected BigDecimal priceBelow(BigDecimal price, BigDecimal offsetFraction) {
        return scale(price.multiply(BigDecimal.ONE.subtract(offsetFraction)));
    }

    /** Preço acima do mercado por uma fração (ex.: descansar um SELL ou tornar um BUY marketable). */
    protected BigDecimal priceAbove(BigDecimal price, BigDecimal offsetFraction) {
        return scale(price.multiply(BigDecimal.ONE.add(offsetFraction)));
    }

    private static BigDecimal scale(BigDecimal value) {
        return value.setScale(PRICE_SCALE, RoundingMode.HALF_UP);
    }
}
