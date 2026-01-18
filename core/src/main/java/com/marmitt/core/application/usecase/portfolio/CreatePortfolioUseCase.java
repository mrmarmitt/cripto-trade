package com.marmitt.core.application.usecase.portfolio;

import com.marmitt.core.domain.portfolio.Portfolio;
import com.marmitt.core.dto.portfolio.CreatePortfolioRequest;
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
                request.name(),
                request.symbol().value(),
                request.strategyName(),
                request.exchangeName());

        try {
            // 1. Validar se já existe portfolio com mesmo nome
            Optional<Portfolio> existingPortfolio = portfolioRepository.findByName(request.name());
            if (existingPortfolio.isPresent()) {
                String errorMsg = "Portfolio with name '" + request.name() + "' already exists";
                log.warn(errorMsg);
                return CreatePortfolioResponse.failure(errorMsg);
            }

            // 2. Validar se estratégia existe
            Optional<TradingStrategy> strategyOptional = strategyRepository.findById(request.strategyId());
            if (strategyOptional.isEmpty()) {
                String errorMsg = "Strategy not found with ID: " + request.strategyId();
                log.error(errorMsg);
                return CreatePortfolioResponse.failure(errorMsg);
            }

            TradingStrategy strategy = strategyOptional.get();

            // 3. Validar se estratégia está ativa
            if (!strategy.isEnabled()) {
                String errorMsg = "Strategy '" + strategy.getStrategyName() + "' is not enabled";
                log.warn(errorMsg);
                return CreatePortfolioResponse.failure(errorMsg);
            }

            // 4. Validar se exchange adapter existe
            if (!exchangeAdapterRepository.hasAdapter(request.exchangeName())) {
                String errorMsg = "Exchange adapter not found: " + request.exchangeName();
                log.error(errorMsg);
                return CreatePortfolioResponse.failure(errorMsg);
            }

            // 5. Validar se já existe portfolio com mesmo símbolo e estratégia (evitar duplicação)
            Optional<Portfolio> duplicatePortfolio = portfolioRepository.findBySymbolAndStrategy(
                    request.symbol().value(),
                    request.strategyId()
            );
            if (duplicatePortfolio.isPresent()) {
                String errorMsg = String.format(
                        "Portfolio already exists for symbol '%s' with strategy '%s'",
                        request.symbol().value(),
                        request.strategyName()
                );
                log.warn(errorMsg);
                return CreatePortfolioResponse.failure(errorMsg);
            }

            // 6. Criar Portfolio com exchange de execução explícita
            UUID portfolioId = UUID.randomUUID();
            Portfolio portfolio = new Portfolio(
                    portfolioId,
                    request.name(),
                    request.strategyId(),
                    request.strategyName(),
                    request.symbol(),
                    request.initialCapital(),
                    request.exchangeName(),  // Exchange para execução de ordens
                    request.allowedMarketDataSources()  // Exchanges permitidas para market data (null = todas)
            );

            // 7. Registrar portfolio no repository
            portfolioRepository.registerPortfolio(portfolio);

            // 8. Associar portfolio ao exchange adapter
            exchangeAdapterRepository.registerPortfolioByAdapter(request.exchangeName(), portfolioId);

            log.info("Portfolio created successfully - ID: {}, Name: {}, Symbol: {}, Strategy: {}, Exchange: {}",
                    portfolioId,
                    portfolio.getName(),
                    portfolio.getSymbol().value(),
                    portfolio.getStrategyName(),
                    request.exchangeName());

            // 9. Criar response de sucesso
            return CreatePortfolioResponse.success(
                    portfolioId,
                    portfolio.getName(),
                    portfolio.getStrategyId(),
                    portfolio.getStrategyName(),
                    portfolio.getSymbol().value(),
                    request.initialCapital().amount().toPlainString(),
                    request.initialCapital().currency(),
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
