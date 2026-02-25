package com.marmitt.core.ports.inbound.runner;

import com.marmitt.core.domain.runner.Position;
import com.marmitt.core.domain.runner.Transaction;
import com.marmitt.core.dto.capital.BuyExecutionContext;
import com.marmitt.core.dto.websocket.data.MarketDataDto;

public interface ProcessTradeSignalPort {
    void execute(MarketDataDto marketData);
    void transactionalPersistBuyAndReserve(BuyExecutionContext context);
    void transactionalPersistSellAndLockPosition(Transaction transaction, Position targetPosition);

}
