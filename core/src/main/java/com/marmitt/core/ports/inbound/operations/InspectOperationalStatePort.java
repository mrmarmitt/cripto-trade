package com.marmitt.core.ports.inbound.operations;

import com.marmitt.core.dto.operations.ExchangeComparison;
import com.marmitt.core.dto.operations.OperationalStateReport;

/**
 * Verifica a consistencia do sistema numa consulta (T55): capital, transacoes, posicoes, DLQ, Safe Mode,
 * runners, estrategias, market data, conexoes e infraestrutura. Somente leitura.
 */
public interface InspectOperationalStatePort {

    /**
     * @param exchange com {@code enabled}, acrescenta a comparacao do registro local com a exchange (saldo
     *                 e trades da janela) — consome cota da API da exchange
     */
    OperationalStateReport inspect(ExchangeComparison exchange);

    /**
     * Sem consultar a exchange.
     */
    default OperationalStateReport inspect() {
        return inspect(ExchangeComparison.NONE);
    }
}
