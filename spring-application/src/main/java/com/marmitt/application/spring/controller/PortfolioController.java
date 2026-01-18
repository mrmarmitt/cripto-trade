package com.marmitt.application.spring.controller;

import com.marmitt.application.spring.controller.dto.portfolio.CreatePortfolioDto;
import com.marmitt.core.domain.Symbol;
import com.marmitt.core.domain.portfolio.Asset;
import com.marmitt.core.domain.portfolio.Portfolio;
import com.marmitt.core.domain.portfolio.Transaction;
import com.marmitt.core.dto.portfolio.CreatePortfolioRequest;
import com.marmitt.core.dto.portfolio.CreatePortfolioResponse;
import com.marmitt.core.dto.portfolio.PortfolioDto;
import com.marmitt.core.dto.portfolio.TransactionDto;
import com.marmitt.core.ports.inbound.portfolio.CreatePortfolioPort;
import com.marmitt.core.ports.outbound.repository.PortfolioRepositoryPort;
import com.marmitt.core.ports.outbound.repository.StrategyRepositoryPort;
import com.marmitt.core.ports.outbound.strategy.TradingStrategy;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/portfolios")
public class PortfolioController {

    private final CreatePortfolioPort createPortfolio;
    private final PortfolioRepositoryPort portfolioRepository;
    private final StrategyRepositoryPort strategyRepository;

    public PortfolioController(
            CreatePortfolioPort createPortfolio,
            PortfolioRepositoryPort portfolioRepository,
            StrategyRepositoryPort strategyRepository
    ) {
        this.createPortfolio = createPortfolio;
        this.portfolioRepository = portfolioRepository;
        this.strategyRepository = strategyRepository;
    }

    /**
     * Cria novo portfolio
     * POST /portfolios
     */
    @PostMapping
    public ResponseEntity<CreatePortfolioResponse> createPortfolio(
            @Valid @RequestBody CreatePortfolioDto dto
    ) {
        log.info("Received request to create portfolio - Name: {}, Symbol: {}, OrderExecutionExchange: {}, AllowedMarketDataSources: {}",
                dto.name(), dto.symbol(), dto.orderExecutionExchange(), dto.allowedMarketDataSources());

        try {
            // Buscar strategy para obter o nome
            Optional<TradingStrategy> strategyOpt = strategyRepository.findById(dto.strategyId());
            if (strategyOpt.isEmpty()) {
                CreatePortfolioResponse errorResponse = CreatePortfolioResponse.failure(
                        "Strategy not found with ID: " + dto.strategyId()
                );
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
            }

            TradingStrategy strategy = strategyOpt.get();

            // Converter DTO para Request
            CreatePortfolioRequest request = CreatePortfolioRequest.builder()
                    .name(dto.name())
                    .strategyId(dto.strategyId())
                    .strategyName(strategy.getStrategyName())
                    .symbol(Symbol.of(dto.symbol()))
                    .initialCapital(Asset.of(dto.initialCapitalAmount(), dto.currency()))
                    .exchangeName(dto.orderExecutionExchange())
                    .allowedMarketDataSources(dto.allowedMarketDataSources())
                    .build();

            // Executar use case
            CreatePortfolioResponse response = createPortfolio.execute(request);

            // Retornar response apropriado
            if (response.portfolioId() != null) {
                return ResponseEntity.status(HttpStatus.CREATED).body(response);
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }

        } catch (IllegalArgumentException e) {
            log.error("Invalid argument in create portfolio request: {}", e.getMessage());
            CreatePortfolioResponse errorResponse = CreatePortfolioResponse.failure(
                    "Invalid argument: " + e.getMessage()
            );
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);

        } catch (Exception e) {
            log.error("Unexpected error creating portfolio", e);
            CreatePortfolioResponse errorResponse = CreatePortfolioResponse.failure(
                    "Internal server error: " + e.getMessage()
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }


    /**
     * Busca portfolio por ID
     * GET /portfolios/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<PortfolioDto> getPortfolioById(@PathVariable UUID id) {
        log.debug("Received request to get portfolio by ID: {}", id);

        Optional<Portfolio> portfolioOpt = portfolioRepository.findById(id);

        if (portfolioOpt.isEmpty()) {
            log.warn("Portfolio not found with ID: {}", id);
            return ResponseEntity.notFound().build();
        }

        PortfolioDto dto = PortfolioDto.fromDomain(portfolioOpt.get());
        return ResponseEntity.ok(dto);
    }

    /**
     * Busca portfolios por símbolo
     * GET /portfolios/symbol/{symbol}
     */
    @GetMapping("/symbol/{symbol}")
    public ResponseEntity<List<PortfolioDto>> getPortfoliosBySymbol(@PathVariable String symbol) {
        log.debug("Received request to get portfolios by symbol: {}", symbol);

        List<Portfolio> portfolios = portfolioRepository.findBySymbol(symbol);
        List<PortfolioDto> portfolioDtos = portfolios.stream()
                .map(PortfolioDto::fromDomain)
                .collect(Collectors.toList());

        log.debug("Found {} portfolio(s) for symbol: {}", portfolioDtos.size(), symbol);
        return ResponseEntity.ok(portfolioDtos);
    }

    /**
     * Busca portfolio por nome
     * GET /portfolios/name/{name}
     */
    @GetMapping("/name/{name}")
    public ResponseEntity<PortfolioDto> getPortfolioByName(@PathVariable String name) {
        log.debug("Received request to get portfolio by name: {}", name);

        Optional<Portfolio> portfolioOpt = portfolioRepository.findByName(name);

        if (portfolioOpt.isEmpty()) {
            log.warn("Portfolio not found with name: {}", name);
            return ResponseEntity.notFound().build();
        }

        PortfolioDto dto = PortfolioDto.fromDomain(portfolioOpt.get());
        return ResponseEntity.ok(dto);
    }

    /**
     * Lista transações de um portfolio
     * GET /portfolios/{id}/transactions
     */
    @GetMapping("/{id}/transactions")
    public ResponseEntity<TransactionsResponse> getTransactionsByPortfolioId(@PathVariable UUID id) {
        log.debug("Received request to get transactions for portfolio: {}", id);

        Optional<Portfolio> portfolioOpt = portfolioRepository.findById(id);

        if (portfolioOpt.isEmpty()) {
            log.warn("Portfolio not found with ID: {}", id);
            return ResponseEntity.notFound().build();
        }

        Portfolio portfolio = portfolioOpt.get();
        List<TransactionDto> transactions = portfolio.getTransactions().stream()
                .map(TransactionDto::fromDomain)
                .collect(Collectors.toList());

        TransactionsResponse response = new TransactionsResponse(
                portfolio.getId(),
                portfolio.getName(),
                transactions,
                transactions.size()
        );

        log.debug("Found {} transactions for portfolio: {}", transactions.size(), id);
        return ResponseEntity.ok(response);
    }

    /**
     * Response wrapper para lista de transações
     */
    public record TransactionsResponse(
            UUID portfolioId,
            String portfolioName,
            List<TransactionDto> transactions,
            int totalCount
    ) {}
}
