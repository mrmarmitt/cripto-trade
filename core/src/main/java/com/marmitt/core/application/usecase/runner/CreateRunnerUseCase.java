package com.marmitt.core.application.usecase.runner;

import com.marmitt.core.domain.portfolio.Portfolio;
import com.marmitt.core.domain.runner.StrategyRunner;
import com.marmitt.core.dto.runner.request.CreateRunnerRequest;
import com.marmitt.core.dto.runner.response.CreateRunnerResponse;
import com.marmitt.core.enums.AccountingPolicyType;
import com.marmitt.core.enums.ExecutionPolicy;
import com.marmitt.core.ports.inbound.runner.CreateRunnerPort;
import com.marmitt.core.ports.outbound.repository.ExchangeAdapterRepositoryPort;
import com.marmitt.core.ports.outbound.repository.PortfolioRepositoryPort;
import com.marmitt.core.ports.outbound.repository.StrategyRepositoryPort;
import com.marmitt.core.ports.outbound.repository.StrategyRunnerRepositoryPort;
import com.marmitt.core.ports.outbound.strategy.TradingStrategy;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

@Slf4j
public class CreateRunnerUseCase implements CreateRunnerPort {

    private final PortfolioRepositoryPort portfolioRepository;
    private final StrategyRepositoryPort strategyRepository;
    private final ExchangeAdapterRepositoryPort exchangeAdapterRepository;
    private final StrategyRunnerRepositoryPort strategyRunnerRepository;

    public CreateRunnerUseCase(
            PortfolioRepositoryPort portfolioRepository,
            StrategyRepositoryPort strategyRepository,
            ExchangeAdapterRepositoryPort exchangeAdapterRepository,
            StrategyRunnerRepositoryPort strategyRunnerRepository
    ) {
        this.portfolioRepository = portfolioRepository;
        this.strategyRepository = strategyRepository;
        this.exchangeAdapterRepository = exchangeAdapterRepository;
        this.strategyRunnerRepository = strategyRunnerRepository;
    }

    @Override
    public CreateRunnerResponse execute(CreateRunnerRequest request) {
        log.info("Creating runner - portfolioId={} strategyId={} symbol={} exchange={}",
                request.portfolioId(), request.strategyId(), request.symbol(), request.exchangeName());

        try {
            Optional<Portfolio> portfolioOpt = portfolioRepository.findById(request.portfolioId());
            if (portfolioOpt.isEmpty()) {
                String msg = "Portfolio not found with ID: " + request.portfolioId();
                log.warn(msg);
                return CreateRunnerResponse.failure(msg);
            }

            Optional<TradingStrategy> strategyOpt = strategyRepository.findById(request.strategyId());
            if (strategyOpt.isEmpty()) {
                String msg = "Strategy not found with ID: " + request.strategyId();
                log.warn(msg);
                return CreateRunnerResponse.failure(msg);
            }

            TradingStrategy strategy = strategyOpt.get();
            if (!strategy.isEnabled()) {
                String msg = "Strategy '" + strategy.getStrategyName() + "' is not enabled";
                log.warn(msg);
                return CreateRunnerResponse.failure(msg);
            }

            if (!exchangeAdapterRepository.hasAdapter(request.exchangeName())) {
                String msg = "Exchange adapter not found: " + request.exchangeName();
                log.warn(msg);
                return CreateRunnerResponse.failure(msg);
            }

            UUID runnerId = UUID.randomUUID();
            String shortCode = runnerId.toString().replace("-", "").substring(0, 4);
            StrategyRunner runner = new StrategyRunner(
                    runnerId,
                    request.portfolioId(),
                    shortCode,
                    request.strategyId(),
                    strategy.getStrategyName(),
                    request.symbol().toUpperCase(),
                    request.exchangeName().toUpperCase(),
                    request.allowedMarketDataSources(),
                    ExecutionPolicy.SINGLE,
                    AccountingPolicyType.FIFO,
                    BigDecimal.ONE,
                    1,
                    1,
                    null
            );

            strategyRunnerRepository.save(runner);
            exchangeAdapterRepository.registerPortfolioByAdapter(request.exchangeName(), request.portfolioId());

            log.info("Runner created successfully - runnerId={} portfolioId={} strategyId={} symbol={} exchange={}",
                    runnerId, request.portfolioId(), request.strategyId(), runner.getSymbol(), runner.getExchangeId());

            return CreateRunnerResponse.success(
                    runnerId,
                    runner.getPortfolioId(),
                    runner.getStrategyId(),
                    runner.getStrategyName(),
                    runner.getSymbol(),
                    runner.getExchangeId(),
                    runner.getAllowedMarketDataSources(),
                    runner.getStatus(),
                    runner.getCreatedAt()
            );

        } catch (IllegalArgumentException e) {
            log.error("Invalid argument creating runner: {}", e.getMessage());
            return CreateRunnerResponse.failure("Invalid argument: " + e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error creating runner: {}", e.getMessage(), e);
            return CreateRunnerResponse.failure("Failed to create runner: " + e.getMessage());
        }
    }
}

