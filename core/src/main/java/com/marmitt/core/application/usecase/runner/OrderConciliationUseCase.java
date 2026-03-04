package com.marmitt.core.application.usecase.runner;

import com.marmitt.core.application.usecase.runner.orderconciliation.ConciliationOrderUpdateExecutor;
import com.marmitt.core.domain.runner.Transaction;
import com.marmitt.core.dto.websocket.data.OrderDataDto;
import com.marmitt.core.ports.inbound.runner.OrderConciliationPort;

/**
 * Orquestrador da conciliacao de ordens.
 *
 * <p>Delega toda a execucao para
 * {@link com.marmitt.core.application.usecase.runner.orderconciliation.ConciliationOrderUpdateExecutor},
 * garantindo a mesma fronteira transacional no fluxo normal e no boot recovery.
 */
public class OrderConciliationUseCase implements OrderConciliationPort {

    private final ConciliationOrderUpdateExecutor conciliationExecutor;

    public OrderConciliationUseCase(ConciliationOrderUpdateExecutor conciliationExecutor) {
        this.conciliationExecutor = conciliationExecutor;
    }

    @Override
    public void execute(OrderDataDto orderData) {
        conciliationExecutor.execute(orderData);
    }
}
