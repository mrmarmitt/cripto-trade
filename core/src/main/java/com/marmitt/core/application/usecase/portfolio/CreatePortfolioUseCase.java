package com.marmitt.core.application.usecase.portfolio;

import com.marmitt.core.domain.portfolio.GlobalBalance;
import com.marmitt.core.domain.portfolio.Portfolio;
import com.marmitt.core.dto.portfolio.request.CreatePortfolioRequest;
import com.marmitt.core.dto.portfolio.response.CreatePortfolioResponse;
import com.marmitt.core.ports.inbound.portfolio.CreatePortfolioPort;
import com.marmitt.core.ports.outbound.repository.GlobalBalanceRepositoryPort;
import com.marmitt.core.ports.outbound.repository.PortfolioRepositoryPort;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

@Slf4j
public class CreatePortfolioUseCase implements CreatePortfolioPort {

    private final PortfolioRepositoryPort portfolioRepository;
    private final GlobalBalanceRepositoryPort globalBalanceRepository;

    public CreatePortfolioUseCase(
            PortfolioRepositoryPort portfolioRepository,
            GlobalBalanceRepositoryPort globalBalanceRepository
    ) {
        this.portfolioRepository = portfolioRepository;
        this.globalBalanceRepository = globalBalanceRepository;
    }

    @Override
    public CreatePortfolioResponse execute(CreatePortfolioRequest request) {
        log.info("Creating portfolio - name={}", request.name());

        try {
            // 1. Validate name uniqueness
            if (portfolioRepository.findByName(request.name()).isPresent()) {
                String msg = "Portfolio with name '" + request.name() + "' already exists";
                log.warn(msg);
                return CreatePortfolioResponse.failure(msg);
            }

            // 2. Create Portfolio (container - name and safe mode only)
            UUID portfolioId = UUID.randomUUID();
            Portfolio portfolio = new Portfolio(portfolioId, request.name());
            portfolioRepository.registerPortfolio(portfolio);

            // 3. Create GlobalBalance with initial capital
            GlobalBalance balance = new GlobalBalance(
                    portfolioId,
                    request.initialCapitalAmount(),
                    request.currency()
            );
            globalBalanceRepository.save(balance);

            log.info("Portfolio created successfully - portfolioId={} name={}",
                    portfolioId, portfolio.getName());

            return CreatePortfolioResponse.success(
                    portfolioId,
                    portfolio.getName(),
                    request.initialCapitalAmount().toPlainString(),
                    request.currency(),
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

