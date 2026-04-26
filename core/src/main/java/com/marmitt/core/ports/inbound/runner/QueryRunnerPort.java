package com.marmitt.core.ports.inbound.runner;

import com.marmitt.core.dto.portfolio.response.TransactionDto;
import com.marmitt.core.dto.runner.RunnerDto;

import java.util.List;
import java.util.UUID;

public interface QueryRunnerPort {

    List<RunnerDto> findByPortfolioId(UUID portfolioId);

    List<TransactionDto> findTransactionsByRunnerId(UUID runnerId);
}

