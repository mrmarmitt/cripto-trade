package com.marmitt.core.ports.inbound.runner;

import com.marmitt.core.domain.runner.Transaction;
import com.marmitt.core.dto.websocket.data.OrderDataDto;

public interface OrderConciliationPort {

    void execute(OrderDataDto orderData);

    void releaseMargin(Transaction transaction);

    void transactionalReleaseMargin(Transaction transaction);

    void transactionalSubmit(Transaction transaction);
}
