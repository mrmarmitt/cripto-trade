package com.marmitt.core.application.usecase.portfolio;

import com.marmitt.core.domain.Symbol;
import com.marmitt.core.domain.portfolio.Asset;
import com.marmitt.core.domain.portfolio.Portfolio;
import com.marmitt.core.dto.portfolio.CreatePortfolioRequest;
import com.marmitt.core.domain.portfolio.contrats.FifoAccountingPolicy;
import com.marmitt.core.dto.portfolio.CreatePortfolioResponse;
import com.marmitt.core.ports.inbound.portfolio.CreatePortfolioPort;
import com.marmitt.core.ports.outbound.repository.ExchangeAdapterRepositoryPort;
import com.marmitt.core.ports.outbound.repository.PortfolioRepositoryPort;
import com.marmitt.core.ports.outbound.repository.StrategyRepositoryPort;
import com.marmitt.core.ports.outbound.strategy.TradingStrategy;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;
import java.util.UUID;

@Slf4j
public class CreatePortfolioUseCase implements CreatePortfolioPort {

    private final PortfolioRepositoryPort portfolioRepository;
    private final StrategyRepositoryPort strategyRepository;
    private final ExchangeAdapterRepositoryPort exchangeAdapterRepository;

    public CreatePortfolioUseCase(
            PortfolioRepositoryPort portfolioRepository,
            StrategyRepositoryPort strategyRepository,
            ExchangeAdapterRepositoryPort exchangeAdapterRepository
    ) {
        this.portfolioRepository = portfolioRepository;
        this.strategyRepository = strategyRepository;
        this.exchangeAdapterRepository = exchangeAdapterRepository;
    }

    @Override
    public CreatePortfolioResponse execute(CreatePortfolioRequest request) {
        log.info("Creating portfolio - Name: {}, Symbol: {}, Strategy: {}, Exchange: {}",
                request.name(), request.symbol(), request.strategyId(), request.exchangeName());

        try {
            // 1. Validar se já existe portfolio com mesmo nome
            Optional<Portfolio> existingPortfolio = portfolioRepository.findByName(request.name());
            if (existingPortfolio.isPresent()) {
                String errorMsg = "Portfolio with name '" + request.name() + "' already exists";
                log.warn(errorMsg);
                return CreatePortfolioResponse.failure(errorMsg);
            }

            // 2. Validar se estratégia existe e está ativa
            Optional<TradingStrategy> strategyOptional = strategyRepository.findById(request.strategyId());
            if (strategyOptional.isEmpty()) {
                String errorMsg = "Strategy not found with ID: " + request.strategyId();
                log.error(errorMsg);
                return CreatePortfolioResponse.failure(errorMsg);
            }

            TradingStrategy strategy = strategyOptional.get();

            if (!strategy.isEnabled()) {
                String errorMsg = "Strategy '" + strategy.getStrategyName() + "' is not enabled";
                log.warn(errorMsg);
                return CreatePortfolioResponse.failure(errorMsg);
            }

            // 3. Validar se exchange adapter existe
            if (!exchangeAdapterRepository.hasAdapter(request.exchangeName())) {
                String errorMsg = "Exchange adapter not found: " + request.exchangeName();
                log.error(errorMsg);
                return CreatePortfolioResponse.failure(errorMsg);
            }

            // 4. Validar se já existe portfolio com mesmo símbolo e estratégia
            Optional<Portfolio> duplicatePortfolio = portfolioRepository.findBySymbolAndStrategy(
                    request.symbol(), request.strategyId()
            );
            if (duplicatePortfolio.isPresent()) {
                String errorMsg = String.format(
                        "Portfolio already exists for symbol '%s' with strategy '%s'",
                        request.symbol(), strategy.getStrategyName()
                );
                log.warn(errorMsg);
                return CreatePortfolioResponse.failure(errorMsg);
            }

            // 5. Criar objetos de domínio
            Symbol symbol = Symbol.of(request.symbol());
            Asset initialCapital = Asset.of(request.initialCapitalAmount(), request.currency());

            // 6. Criar Portfolio
            UUID portfolioId = UUID.randomUUID();
            Portfolio portfolio = new Portfolio(
                    portfolioId,
                    request.name(),
                    request.strategyId(),
                    strategy.getStrategyName(),
                    symbol,
                    new FifoAccountingPolicy(),
                    initialCapital,
                    request.exchangeName(),
                    request.allowedMarketDataSources()
            );

            // 7. Registrar portfolio no repository
            portfolioRepository.registerPortfolio(portfolio);

            // 8. Associar portfolio ao exchange adapter
            exchangeAdapterRepository.registerPortfolioByAdapter(request.exchangeName(), portfolioId);

            log.info("Portfolio created successfully - ID: {}, Name: {}, Symbol: {}, Strategy: {}, Exchange: {}",
                    portfolioId, portfolio.getName(), portfolio.getSymbol().value(),
                    portfolio.getStrategyName(), request.exchangeName());

            // 9. Criar response
            return CreatePortfolioResponse.success(
                    portfolioId,
                    portfolio.getName(),
                    portfolio.getStrategyId(),
                    portfolio.getStrategyName(),
                    portfolio.getSymbol().value(),
                    initialCapital.amount().toPlainString(),
                    initialCapital.currency(),
                    request.exchangeName(),
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
