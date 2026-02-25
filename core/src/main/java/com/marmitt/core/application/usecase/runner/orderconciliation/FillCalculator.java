package com.marmitt.core.application.usecase.runner.orderconciliation;

import com.marmitt.core.domain.runner.Transaction;
import com.marmitt.core.dto.websocket.data.OrderDataDto;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Calcula o incremento marginal de execucao a partir dos dados cumulativos da exchange
 * e aplica a transicao de estado na transacao.
 *
 * <p><b>Por que calcular incremento?</b> A exchange reporta quantidades e precos de forma
 * cumulativa — cada evento contem o total executado ate aquele momento, nao apenas o que
 * foi executado no ultimo evento. Os handlers de fill ({@link BuyFillHandler},
 * {@link SellFillHandler}) operam sobre incrementos para atualizar a posicao corretamente
 * em cenarios de execucao parcial sequencial.
 *
 * <p><b>Preco do incremento (WAP marginal):</b> como o preco reportado e o WAP cumulativo,
 * o preco do incremento e calculado como:
 * <pre>
 *   fillPrice = (newQty × newWap - prevQty × prevWap) / fillIncrement
 * </pre>
 * Isso isola o preco medio apenas do que foi executado neste evento, preservando
 * a precisao contabil dos {@link com.marmitt.core.domain.runner.TransactionMatch matches}.
 * Para o primeiro fill (prevQty = 0), o WAP cumulativo e usado diretamente.
 */
class FillCalculator {

    /**
     * Avanca o estado da transacao para PARTIAL ou FILLED e retorna o incremento marginal.
     *
     * @param transaction transacao a ser atualizada (SUBMITTED ou PARTIAL)
     * @param orderData   evento da exchange com quantidade e preco cumulativos
     * @param isFinal     {@code true} para FILLED, {@code false} para PARTIALLY_FILLED
     * @return par (fillIncrement, fillPrice) representando apenas este evento de execucao
     */
    public FillComputation compute(Transaction transaction, OrderDataDto orderData, boolean isFinal) {
        BigDecimal prevQty = transaction.getEffectiveExecutedQuantity();
        BigDecimal prevPrice = transaction.getEffectiveExecutedPrice();

        if (isFinal) {
            transaction.fill(orderData.executedQuantity(), orderData.executedPrice());
        } else {
            transaction.partialFill(orderData.executedQuantity(), orderData.executedPrice());
        }

        BigDecimal fillIncrement = transaction.getExecutedQuantity().subtract(prevQty);
        BigDecimal fillPrice = computeFillPrice(prevQty, prevPrice,
                transaction.getExecutedQuantity(), transaction.getExecutedPrice(), fillIncrement);

        return new FillComputation(fillIncrement, fillPrice);
    }

    private static BigDecimal computeFillPrice(BigDecimal prevQty, BigDecimal prevWap,
                                               BigDecimal newQty, BigDecimal newWap,
                                               BigDecimal increment) {
        if (prevQty.compareTo(BigDecimal.ZERO) == 0) {
            return newWap;
        }
        BigDecimal newValue = newQty.multiply(newWap);
        BigDecimal prevValue = prevQty.multiply(prevWap);
        return newValue.subtract(prevValue).divide(increment, 8, RoundingMode.HALF_UP);
    }

    /**
     * Resultado do calculo de fill para um unico evento de execucao.
     *
     * @param fillIncrement quantidade marginal executada neste evento (base asset)
     * @param fillPrice     preco medio ponderado do incremento (quote asset),
     *                      calculado a partir da diferenca entre WAPs cumulativos
     */
    public record FillComputation(BigDecimal fillIncrement, BigDecimal fillPrice) {
    }
}
