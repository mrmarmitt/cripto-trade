package com.marmitt.core.application.usecase.portfolio;

import com.marmitt.core.domain.portfolio.GlobalBalance;
import com.marmitt.core.domain.portfolio.Portfolio;
import com.marmitt.core.domain.runner.StrategyRunner;
import com.marmitt.core.dto.portfolio.CreatePortfolioRequest;
import com.marmitt.core.dto.portfolio.CreatePortfolioResponse;
import com.marmitt.core.enums.AccountingPolicyType;
import com.marmitt.core.enums.ExecutionPolicy;
import com.marmitt.core.ports.inbound.portfolio.CreatePortfolioPort;
import com.marmitt.core.ports.outbound.repository.ExchangeAdapterRepositoryPort;
import com.marmitt.core.ports.outbound.repository.GlobalBalanceRepositoryPort;
import com.marmitt.core.ports.outbound.repository.PortfolioRepositoryPort;
import com.marmitt.core.ports.outbound.repository.StrategyRepositoryPort;
import com.marmitt.core.ports.outbound.repository.StrategyRunnerRepositoryPort;
import com.marmitt.core.ports.outbound.strategy.TradingStrategy;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

@Slf4j
public class CreatePortfolioUseCase implements CreatePortfolioPort {

    private final PortfolioRepositoryPort portfolioRepository;
    private final StrategyRepositoryPort strategyRepository;
    private final ExchangeAdapterRepositoryPort exchangeAdapterRepository;
    private final GlobalBalanceRepositoryPort globalBalanceRepository;
    private final StrategyRunnerRepositoryPort strategyRunnerRepository;

    public CreatePortfolioUseCase(
            PortfolioRepositoryPort portfolioRepository,
            StrategyRepositoryPort strategyRepository,
            ExchangeAdapterRepositoryPort exchangeAdapterRepository,
            GlobalBalanceRepositoryPort globalBalanceRepository,
            StrategyRunnerRepositoryPort strategyRunnerRepository
    ) {
        this.portfolioRepository = portfolioRepository;
        this.strategyRepository = strategyRepository;
        this.exchangeAdapterRepository = exchangeAdapterRepository;
        this.globalBalanceRepository = globalBalanceRepository;
        this.strategyRunnerRepository = strategyRunnerRepository;
    }

    @Override
    public CreatePortfolioResponse execute(CreatePortfolioRequest request) {
        log.info("Creating portfolio - name={} symbol={} strategyId={} exchange={}",
                request.name(), request.symbol(), request.strategyId(), request.exchangeName());

        try {
            // 1. Validate name uniqueness
            if (portfolioRepository.findByName(request.name()).isPresent()) {
                String msg = "Portfolio with name '" + request.name() + "' already exists";
                log.warn(msg);
                return CreatePortfolioResponse.failure(msg);
            }

            // 2. Validate strategy exists and is enabled
            Optional<TradingStrategy> strategyOpt = strategyRepository.findById(request.strategyId());
            if (strategyOpt.isEmpty()) {
                String msg = "Strategy not found with ID: " + request.strategyId();
                log.error(msg);
                return CreatePortfolioResponse.failure(msg);
            }

            TradingStrategy strategy = strategyOpt.get();
            if (!strategy.isEnabled()) {
                String msg = "Strategy '" + strategy.getStrategyName() + "' is not enabled";
                log.warn(msg);
                return CreatePortfolioResponse.failure(msg);
            }

            // 3. Validate exchange adapter exists
            if (!exchangeAdapterRepository.hasAdapter(request.exchangeName())) {
                String msg = "Exchange adapter not found: " + request.exchangeName();
                log.error(msg);
                return CreatePortfolioResponse.failure(msg);
            }

            // 4. (Uniqueness of runner per strategy+symbol+exchange is enforced by the DB unique index)

            // 5. Create Portfolio (container — name and safe mode only)
            UUID portfolioId = UUID.randomUUID();
            Portfolio portfolio = new Portfolio(portfolioId, request.name());
            portfolioRepository.registerPortfolio(portfolio);

            // 6. Create GlobalBalance with initial capital
            GlobalBalance balance = new GlobalBalance(
                    portfolioId,
                    request.initialCapitalAmount(),
                    request.currency()
            );
            globalBalanceRepository.save(balance);

            // 7. Create StrategyRunner (holds strategy, symbol, exchange and operational config)
            UUID runnerId = UUID.randomUUID();
            String shortCode = runnerId.toString().replace("-", "").substring(0, 4);
            StrategyRunner runner = new StrategyRunner(
                    runnerId,
                    portfolioId,
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

            // 8. Register portfolio routing in exchange adapter
            exchangeAdapterRepository.registerPortfolioByAdapter(request.exchangeName(), portfolioId);

            log.info("Portfolio created successfully - portfolioId={} runnerId={} name={} symbol={} exchange={}",
                    portfolioId, runnerId, portfolio.getName(), runner.getSymbol(), runner.getExchangeId());

            return CreatePortfolioResponse.success(
                    portfolioId,
                    portfolio.getName(),
                    runner.getStrategyId(),
                    runner.getStrategyName(),
                    runner.getSymbol(),
                    request.initialCapitalAmount().toPlainString(),
                    request.currency(),
                    runner.getExchangeId(),
                    portfolio.getCreatedAt()
            );

        } catch (IllegalArgumentException e) {
            log.error("Invalid argument creating portfolio: {}", e.getMessage());
            return CreatePortfolioResponse.failure("Invalid argument: " + e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error creating portfolio: {}", e.getMessage(), e);
            return CreatePortfolioResponse.failure("Failed to create portfolio: " + e.getMessage());
        }
    }
}
