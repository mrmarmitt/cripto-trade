package com.marmitt.strategy.impl.scenario;

import java.math.BigDecimal;

/**
 * Parâmetros compartilhados das estratégias de cenário para testnet (T35).
 *
 * <p>Offsets são <b>frações sobre o preço de mercado corrente</b> (ex.: {@code 0.50} = 50%),
 * nunca valores absolutos, para adaptarem-se a qualquer símbolo/preço. Os defaults devem caber
 * na banda do filtro {@code PERCENT_PRICE}/{@code PERCENT_PRICE_BY_SIDE} do símbolo (ver T35/T6):
 * o offset de "descansar" precisa afastar o suficiente para a ordem não preencher, mas dentro da
 * banda; o offset marketable precisa cruzar o spread.
 */
public record ScenarioStrategyConfig(
        BigDecimal restingOffset,        // afastamento p/ a ordem descansar sem preencher (0 < x < 1)
        BigDecimal fillOffset,           // afastamento p/ tornar a ordem marketable (> 0)
        BigDecimal quantity,             // quantidade absoluta por ordem (> 0)
        BigDecimal overAllocationFactor  // multiplicador (> 1) p/ estourar o capital disponível
) {

    public ScenarioStrategyConfig {
        requireFraction(restingOffset, "restingOffset");
        requirePositive(fillOffset, "fillOffset");
        requirePositive(quantity, "quantity");
        if (overAllocationFactor == null || overAllocationFactor.compareTo(BigDecimal.ONE) <= 0) {
            throw new IllegalArgumentException("overAllocationFactor must be > 1");
        }
    }

    /**
     * Defaults sensatos para testnet. Ajuste os offsets aos filtros do símbolo antes de operar.
     */
    public static ScenarioStrategyConfig defaultConfig() {
        return new ScenarioStrategyConfig(
                new BigDecimal("0.50"),   // 50% longe do mercado — descansa sem preencher
                new BigDecimal("0.02"),   // 2% além do mercado — marketable (cruza o spread)
                new BigDecimal("0.001"),  // 0.001 unidade por ordem
                new BigDecimal("2")       // 2x o capital disponível
        );
    }

    private static void requireFraction(BigDecimal value, String name) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0 || value.compareTo(BigDecimal.ONE) >= 0) {
            throw new IllegalArgumentException(name + " must be between 0.0 and 1.0 (exclusive)");
        }
    }

    private static void requirePositive(BigDecimal value, String name) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
