package com.marmitt.core.ports.inbound.operations;

import com.marmitt.core.dto.operations.OperationalStateReport;

/**
 * Verifica a consistencia do sistema numa consulta (T55): capital, transacoes, posicoes, DLQ, Safe Mode,
 * runners, estrategias, market data, conexoes e infraestrutura. Somente leitura.
 */
public interface InspectOperationalStatePort {

    OperationalStateReport inspect();
}
