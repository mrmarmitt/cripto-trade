package com.marmitt.core.ports.inbound.runner;

import com.marmitt.core.domain.runner.Position;
import com.marmitt.core.domain.runner.StrategyRunner;
import com.marmitt.core.domain.runner.Transaction;
import com.marmitt.core.dto.capital.CapitalRequest;
import com.marmitt.core.dto.websocket.data.MarketDataDto;

public interface ProcessTradeSignalPort {
    void execute(MarketDataDto marketData);
    void transactionalPersistBuyAndReserve(Transaction transaction, CapitalRequest capitalRequest, StrategyRunner runner);
    void transactionalPersistSellAndLockPosition(Transaction transaction, Position targetPosition);

}
