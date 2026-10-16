package com.marmitt.binance.request;

import com.marmitt.core.enums.OrderType;

/**
 * Regras da Binance por tipo de ordem, compartilhadas pelo envio REST e pela WS API (T45).
 *
 * <p>Tipos com preco-limite enviam {@code price} e {@code timeInForce}; os demais (ex.: MARKET)
 * nao podem enviar nenhum dos dois — a Binance rejeita {@code MARKET} com {@code price}.
 */
public final class BinanceOrderTypeRules {

    private BinanceOrderTypeRules() {
    }

    public static boolean hasLimitPrice(OrderType orderType) {
        return orderType == OrderType.LIMIT
                || orderType == OrderType.STOP_LOSS_LIMIT
                || orderType == OrderType.TAKE_PROFIT_LIMIT;
    }
}
