package com.marmitt.core.application.usecase.reconciliation;

import com.marmitt.core.domain.runner.Transaction;
import com.marmitt.core.dto.reconciliation.ReconciliationReportDto;
import com.marmitt.core.dto.reconciliation.ReconciliationRequest;
import com.marmitt.core.dto.reconciliation.TradeExecutionDto;
import com.marmitt.core.ports.inbound.reconciliation.ReconcileTradesPort;
import com.marmitt.core.ports.outbound.exchange.TradeHistoryQueryPort;
import com.marmitt.core.ports.outbound.repository.StrategyRunnerRepositoryPort;

import java.util.List;

public class ReconcileTradesUseCase implements ReconcileTradesPort {

    private final TradeHistoryQueryPort tradeHistoryQueryPort;
    private final StrategyRunnerRepositoryPort strategyRunnerRepository;

    public ReconcileTradesUseCase(TradeHistoryQueryPort tradeHistoryQueryPort,
                                  StrategyRunnerRepositoryPort strategyRunnerRepository) {
        this.tradeHistoryQueryPort = tradeHistoryQueryPort;
        this.strategyRunnerRepository = strategyRunnerRepository;
    }

    @Override
    public ReconciliationReportDto reconcile(ReconciliationRequest request) {
        List<TradeExecutionDto> exchangeFills = tradeHistoryQueryPort.fetchTrades(
                request.symbol(), request.from(), request.to());

        List<Transaction> localTransactions = strategyRunnerRepository.findFilledBySymbolAndPeriod(
                request.symbol(), request.exchangeId(), request.from(), request.to());

        return ReconciliationMatcher.match(exchangeFills, localTransactions, request);
    }
}
