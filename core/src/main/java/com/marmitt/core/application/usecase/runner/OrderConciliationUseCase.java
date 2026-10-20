package com.marmitt.core.application.usecase.runner;

import com.marmitt.core.application.usecase.runner.orderconciliation.ConciliationOrderUpdateExecutor;
import com.marmitt.core.domain.runner.Transaction;
import com.marmitt.core.dto.websocket.data.OrderDataDto;
import com.marmitt.core.ports.inbound.runner.OrderConciliationPort;
import lombok.extern.slf4j.Slf4j;

/**
 * Orquestrador da conciliacao de ordens.
 *
 * <p>Delega toda a execucao para
 * {@link com.marmitt.core.application.usecase.runner.orderconciliation.ConciliationOrderUpdateExecutor},
 * garantindo a mesma fronteira transacional no fluxo normal e no boot recovery.
 */
@Slf4j
public class OrderConciliationUseCase implements OrderConciliationPort {

    private final ConciliationOrderUpdateExecutor conciliationExecutor;
    private final ConsecutiveRejectionGuard rejectionGuard;

    public OrderConciliationUseCase(ConciliationOrderUpdateExecutor conciliationExecutor,
                                    ConsecutiveRejectionGuard rejectionGuard) {
        this.conciliationExecutor = conciliationExecutor;
        this.rejectionGuard = rejectionGuard;
    }

    @Override
    public void execute(OrderDataDto orderData) {
        conciliationExecutor.execute(orderData);
        // T25: depois de conciliada, a ordem conta na sequencia de rejeicoes do runner. Falha aqui nao
        // desfaz a conciliacao.
        try {
            rejectionGuard.onOrderUpdate(orderData);
        } catch (RuntimeException e) {
            log.error("orderConciliation: rejection guard failed clientOrderId={} - {}",
                    orderData.clientOrderId(), e.getMessage(), e);
        }
    }
}
