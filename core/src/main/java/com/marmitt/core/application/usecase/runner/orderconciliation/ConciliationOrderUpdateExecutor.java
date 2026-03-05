package com.marmitt.core.application.usecase.runner.orderconciliation;

import com.marmitt.core.domain.runner.Transaction;
import com.marmitt.core.dto.websocket.data.OrderDataDto;

/**
 * Contrato de execucao da conciliacao de ordens com fronteira transacional.
 *
 * <p>Objetivo:
 * unificar o caminho de reconciliacao usado no fluxo normal de ordem
 * (listener WebSocket) e no boot recovery, garantindo o mesmo comportamento
 * de persistencia e eventos AFTER_COMMIT.
 */
public interface ConciliationOrderUpdateExecutor {

    void execute(OrderDataDto orderData);

    void submitTransaction(Transaction transaction);

    void processFill(Transaction transaction, OrderDataDto orderData, boolean isFinal);

    void releaseMargin(Transaction transaction);
}
