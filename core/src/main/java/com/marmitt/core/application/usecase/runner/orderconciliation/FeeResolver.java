package com.marmitt.core.application.usecase.runner.orderconciliation;

import com.marmitt.core.dto.websocket.data.OrderDataDto;

import java.math.BigDecimal;

/**
 * Classifica a fee de um evento de execucao pelo ativo em que a exchange a cobrou (T46).
 *
 * <p>O ativo decide o efeito contabil: fee no ativo base reduz a quantidade recebida, fee no ativo
 * de cotacao reduz o valor em quote, e fee em terceiro ativo (ex.: BNB) nao e suportada nesta
 * versao. A classificacao fica aqui para que {@link BuyFillHandler} e {@link SellFillHandler}
 * apliquem a mesma regra.
 */
class FeeResolver {

    enum Kind {
        /** Sem fee no evento. */
        NONE,
        /** Cobrada no ativo base do par (ex.: BTC em BTCUSDT). */
        BASE,
        /** Cobrada no ativo de cotacao do par (ex.: USDT em BTCUSDT). */
        QUOTE,
        /** Cobrada em ativo fora do par (ex.: BNB). Nao suportada: so registrada em log. */
        UNSUPPORTED,
        /** Produtor nao informou o ativo; o core aplica o comportamento legado. */
        UNKNOWN
    }

    record ResolvedFee(Kind kind, BigDecimal amount, String asset) {
    }

    ResolvedFee resolve(OrderDataDto orderData) {
        BigDecimal amount = orderData.fee();
        String asset = orderData.feeAsset();
        if (amount == null || amount.signum() == 0) {
            return new ResolvedFee(Kind.NONE, BigDecimal.ZERO, asset);
        }
        if (asset == null || asset.isBlank()) {
            return new ResolvedFee(Kind.UNKNOWN, amount, null);
        }
        if (asset.equalsIgnoreCase(orderData.symbol().getBaseAsset())) {
            return new ResolvedFee(Kind.BASE, amount, asset);
        }
        if (asset.equalsIgnoreCase(orderData.symbol().getQuoteAsset())) {
            return new ResolvedFee(Kind.QUOTE, amount, asset);
        }
        return new ResolvedFee(Kind.UNSUPPORTED, amount, asset);
    }
}
