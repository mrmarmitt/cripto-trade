package com.marmitt.core.application.usecase.runner.processsignal;

import com.marmitt.core.ports.outbound.exchange.rest.OrderQuantityNormalizerPort;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Resolve o {@link OrderQuantityNormalizerPort} da exchange do runner e aplica a
 * normalização de quantidade e preço antes do persist da transação.
 *
 * <p>Quando a exchange não tem normalizador registrado (ex.: MOCK), os valores originais
 * são retornados sem modificação — o caso de uso continua funcionando sem alinhamento de
 * filtro para esse provider.
 */
class OrderNormalizer {

    private final Map<String, OrderQuantityNormalizerPort> byExchange;

    OrderNormalizer(List<OrderQuantityNormalizerPort> normalizers) {
        Map<String, OrderQuantityNormalizerPort> map = new HashMap<>();
        for (OrderQuantityNormalizerPort normalizer : normalizers) {
            map.put(key(normalizer.getExchangeName()), normalizer);
        }
        this.byExchange = map;
    }

    NormalizedOrder normalize(String exchangeName, String symbol, BigDecimal quantity, BigDecimal price) {
        OrderQuantityNormalizerPort normalizer = exchangeName == null ? null : byExchange.get(key(exchangeName));
        if (normalizer == null) {
            return new NormalizedOrder(quantity, price);
        }
        return new NormalizedOrder(
                normalizer.normalizeQuantity(symbol, quantity),
                normalizer.normalizePrice(symbol, price));
    }

    private static String key(String exchangeName) {
        return exchangeName.toUpperCase(Locale.ROOT);
    }

    record NormalizedOrder(BigDecimal quantity, BigDecimal price) {
    }
}
