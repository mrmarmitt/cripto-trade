package com.marmitt.strategy.impl.scenario;

import com.marmitt.core.dto.strategy.PendingOrderDto;
import com.marmitt.core.dto.strategy.StrategyContextDto;
import com.marmitt.core.enums.TradingAction;
import com.marmitt.core.ports.outbound.strategy.TradingStrategy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

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
    private final AtomicInteger cyclesStarted = new AtomicInteger(0);
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

    /**
     * Consome um ciclo do orçamento, se houver. Retorna {@code true} (e incrementa) quando a
     * estratégia ainda pode iniciar um novo ciclo; {@code false} quando o teto {@code maxCycles}
     * já foi atingido — sinal para a estratégia ficar inerte (HOLD). Sem teto ({@code maxCycles=0})
     * sempre retorna {@code true}. Cada subclasse chama isto no ponto que inicia o seu ciclo
     * (abertura de BUY, colocação de SELL descansando, etc.), não na limpeza (cancelamento).
     */
    protected boolean tryStartCycle() {
        if (!config.hasCycleLimit()) {
            return true;
        }
        int limit = config.maxCycles();
        while (true) {
            int current = cyclesStarted.get();
            if (current >= limit) {
                return false;
            }
            if (cyclesStarted.compareAndSet(current, current + 1)) {
                return true;
            }
        }
    }

    /** Número de ciclos já iniciados (para observabilidade/teste). */
    protected int cyclesStarted() {
        return cyclesStarted.get();
    }

    /**
     * {@code true} quando há teto e ele já foi totalmente consumido. Ao contrário de
     * {@link #tryStartCycle()}, <b>não</b> consome orçamento — serve para legs de setup (ex.:
     * reabertura de posição) ficarem inertes após o teto sem serem contadas como ciclo.
     */
    protected boolean budgetExhausted() {
        return config.hasCycleLimit() && cyclesStarted.get() >= config.maxCycles();
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
