package com.marmitt.core.ports.inbound.portfolio;

import com.marmitt.core.dto.capital.ExecutionConfirmation;

/**
 * Port de entrada para confirmação de execução (total ou parcial) de uma ordem.
 * <p>
 * <b>Natureza:</b> Assíncrono, fire-and-forget com garantia at-least-once.
 * <p>
 * Deve ser invocado após cada {@code TransactionMatch} ser persistido.
 * O Portfolio garante idempotência via {@code matchId} — duplicatas descartadas.
 * Em caso de falha, o evento é reenfileirado com backoff exponencial (default: 5 tentativas).
 * Após esgotar as tentativas, o evento vai para a DLQ.
 * <p>
 * Efeito no {@code GlobalBalance}: converte margem de Reserved → Realized.
 * {@code reserved -= totalCost}, {@code realized += pnlAmount}, {@code totalFeesPaid += feeConverted}.
 *
 * @see <a href="docs/IMPLEMENTATION_GUIDE.md">IG Seção 5.2.2</a>
 */
public interface ConfirmExecutionPort {

    /**
     * Notifica o Portfolio sobre execução (total ou parcial) de uma ordem.
     *
     * @param confirmation payload com matchId, quantidade executada, preço, fee e totalCost
     */
    void confirmExecution(ExecutionConfirmation confirmation);
}
