package com.marmitt.core.ports.inbound.runner;

import com.marmitt.core.dto.portfolio.response.TransactionDto;
import com.marmitt.core.dto.runner.response.RunnerDto;

import java.util.List;
import java.util.UUID;

public interface QueryRunnerPort {

    List<RunnerDto> findAll();

    List<RunnerDto> findByPortfolioId(UUID portfolioId);

    List<TransactionDto> findTransactionsByRunnerId(UUID runnerId);
}


