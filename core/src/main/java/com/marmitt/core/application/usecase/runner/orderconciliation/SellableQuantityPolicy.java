package com.marmitt.core.application.usecase.runner.orderconciliation;

import com.marmitt.core.ports.outbound.exchange.rest.OrderQuantityNormalizerPort;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Decide se uma quantidade residual de posicao ainda pode ser vendida na exchange (T46).
 *
 * <p>Com a fee de BUY cobrada no ativo base, o lote recebe quantidade liquida, e a venda e
 * alinhada ao {@code stepSize}; sobra um residuo menor que um passo que nenhuma ordem consegue
 * vender. A regra de quando baixar esse residuo e do core; o conhecimento do {@code stepSize} e do
 * adapter, consultado via {@link OrderQuantityNormalizerPort}: quantidade que normaliza para zero
 * nao e vendavel. Exchange sem normalizador (ex.: MOCK) aceita qualquer quantidade.
 */
class SellableQuantityPolicy {

    private final Map<String, OrderQuantityNormalizerPort> byExchange;

    SellableQuantityPolicy(List<OrderQuantityNormalizerPort> normalizers) {
        Map<String, OrderQuantityNormalizerPort> map = new HashMap<>();
        for (OrderQuantityNormalizerPort normalizer : normalizers) {
            map.put(key(normalizer.getExchangeName()), normalizer);
        }
        this.byExchange = map;
    }

    boolean isSellable(String exchangeName, String symbol, BigDecimal quantity) {
        if (quantity == null || quantity.signum() <= 0) {
            return false;
        }
        OrderQuantityNormalizerPort normalizer = exchangeName == null ? null : byExchange.get(key(exchangeName));
        if (normalizer == null) {
            return true;
        }
        return normalizer.normalizeQuantity(symbol, quantity).signum() > 0;
    }

    private static String key(String exchangeName) {
        return exchangeName.toUpperCase(Locale.ROOT);
    }
}
