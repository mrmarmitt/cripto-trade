package com.marmitt.application.spring.controller;

import com.marmitt.application.spring.controller.dto.portfolio.CreatePortfolioDto;
import com.marmitt.core.dto.portfolio.CreatePortfolioRequest;
import com.marmitt.core.dto.portfolio.CreatePortfolioResponse;
import com.marmitt.core.dto.portfolio.PortfolioDto;
import com.marmitt.core.dto.portfolio.TransactionDto;
import com.marmitt.core.dto.runner.CreateRunnerRequest;
import com.marmitt.core.dto.runner.CreateRunnerResponse;
import com.marmitt.core.dto.runner.RunnerDto;
import com.marmitt.core.ports.inbound.portfolio.CreatePortfolioPort;
import com.marmitt.core.ports.inbound.portfolio.QueryPortfolioPort;
import com.marmitt.core.ports.inbound.runner.CreateRunnerPort;
import com.marmitt.core.ports.inbound.runner.QueryRunnerPort;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/portfolios")
public class PortfolioController {

    private final CreatePortfolioPort createPortfolio;
    private final QueryPortfolioPort queryPortfolio;
    private final CreateRunnerPort createRunner;
    private final QueryRunnerPort queryRunner;

    public PortfolioController(
            CreatePortfolioPort createPortfolio,
            QueryPortfolioPort queryPortfolio,
            CreateRunnerPort createRunner,
            QueryRunnerPort queryRunner
    ) {
        this.createPortfolio = createPortfolio;
        this.queryPortfolio = queryPortfolio;
        this.createRunner = createRunner;
        this.queryRunner = queryRunner;
    }

    /**
     * Cria novo portfolio
     * POST /portfolios
     */
    @PostMapping
    public ResponseEntity<CreatePortfolioResponse> createPortfolio(
            @Valid @RequestBody CreatePortfolioDto dto
    ) {
        log.info("Received request to create portfolio - Name: {}, InitialCapital: {}, Currency: {}",
                dto.name(), dto.initialCapitalAmount(), dto.currency());

        try {
            CreatePortfolioRequest request = CreatePortfolioRequest.builder()
                    .name(dto.name())
                    .initialCapitalAmount(dto.initialCapitalAmount())
                    .currency(dto.currency())
                    .build();

            CreatePortfolioResponse response = createPortfolio.execute(request);

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
     * Cria um novo runner para um portfolio existente
     * POST /portfolios/{id}/runners
     */
    @PostMapping("/{id}/runners")
    public ResponseEntity<CreateRunnerResponse> createRunner(
            @PathVariable UUID id,
            @Valid @RequestBody CreateRunnerDto dto
    ) {
        log.info("Received request to create runner - PortfolioId: {}, StrategyId: {}, Symbol: {}, Exchange: {}",
                id, dto.strategyId(), dto.symbol(), dto.exchangeName());

        try {
            CreateRunnerRequest request = CreateRunnerRequest.builder()
                    .portfolioId(id)
                    .strategyId(dto.strategyId())
                    .symbol(dto.symbol())
                    .exchangeName(dto.exchangeName())
                    .allowedMarketDataSources(dto.allowedMarketDataSources())
                    .build();

            CreateRunnerResponse response = createRunner.execute(request);

            if (response.runnerId() != null) {
                return ResponseEntity.status(HttpStatus.CREATED).body(response);
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);

        } catch (IllegalArgumentException e) {
            log.error("Invalid argument in create runner request: {}", e.getMessage());
            CreateRunnerResponse errorResponse = CreateRunnerResponse.failure(
                    "Invalid argument: " + e.getMessage()
            );
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);

        } catch (Exception e) {
            log.error("Unexpected error creating runner", e);
            CreateRunnerResponse errorResponse = CreateRunnerResponse.failure(
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

        Optional<PortfolioDto> portfolioOpt = queryPortfolio.findById(id);

        if (portfolioOpt.isEmpty()) {
            log.warn("Portfolio not found with ID: {}", id);
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(portfolioOpt.get());
    }

    /**
     * Busca portfolios por símbolo (query convenience)
     * GET /portfolios/symbol/{symbol}
     */
    @GetMapping("/symbol/{symbol}")
    public ResponseEntity<List<PortfolioDto>> getPortfoliosBySymbol(@PathVariable String symbol) {
        log.debug("Received request to get portfolios by symbol: {}", symbol);

        List<PortfolioDto> portfolios = queryPortfolio.findBySymbol(symbol);

        log.debug("Found {} portfolio(s) for symbol: {}", portfolios.size(), symbol);
        return ResponseEntity.ok(portfolios);
    }

    /**
     * Busca portfolio por nome (query convenience)
     * GET /portfolios/name/{name}
     */
    @GetMapping("/name/{name}")
    public ResponseEntity<PortfolioDto> getPortfolioByName(@PathVariable String name) {
        log.debug("Received request to get portfolio by name: {}", name);

        Optional<PortfolioDto> portfolioOpt = queryPortfolio.findByName(name);

        if (portfolioOpt.isEmpty()) {
            log.warn("Portfolio not found with name: {}", name);
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(portfolioOpt.get());
    }

    /**
     * Lista transações agregadas dos runners de um portfolio
     * GET /portfolios/{id}/transactions
     */
    @GetMapping("/{id}/transactions")
    public ResponseEntity<AggregatedTransactionsResponse> getTransactionsByPortfolioId(@PathVariable UUID id) {
        log.debug("Received request to get transactions for portfolio: {}", id);

        Optional<PortfolioDto> portfolioOpt = queryPortfolio.findById(id);

        if (portfolioOpt.isEmpty()) {
            log.warn("Portfolio not found with ID: {}", id);
            return ResponseEntity.notFound().build();
        }

        PortfolioDto portfolio = portfolioOpt.get();
        List<TransactionDto> transactions = queryPortfolio.findTransactionsByPortfolioId(id);

        AggregatedTransactionsResponse response = new AggregatedTransactionsResponse(
                portfolio.id(),
                portfolio.name(),
                transactions,
                transactions.size()
        );

        log.debug("Found {} transactions for portfolio: {}", transactions.size(), id);
        return ResponseEntity.ok(response);
    }

    /**
     * Lista runners de um portfolio
     * GET /portfolios/{id}/runners
     */
    @GetMapping("/{id}/runners")
    public ResponseEntity<List<RunnerDto>> getRunnersByPortfolioId(@PathVariable UUID id) {
        log.debug("Received request to get runners for portfolio: {}", id);

        Optional<PortfolioDto> portfolioOpt = queryPortfolio.findById(id);
        if (portfolioOpt.isEmpty()) {
            log.warn("Portfolio not found with ID: {}", id);
            return ResponseEntity.notFound().build();
        }

        List<RunnerDto> runners = queryRunner.findByPortfolioId(id);
        log.debug("Found {} runner(s) for portfolio: {}", runners.size(), id);
        return ResponseEntity.ok(runners);
    }

    public record AggregatedTransactionsResponse(
            UUID portfolioId,
            String portfolioName,
            List<TransactionDto> transactions,
            int totalCount
    ) {}

    public record CreateRunnerDto(
            @NotNull(message = "Strategy ID is required")
            UUID strategyId,

            @NotBlank(message = "Symbol is required")
            String symbol,

            @NotBlank(message = "Exchange name for order execution is required")
            String exchangeName,

            Set<String> allowedMarketDataSources
    ) {}
}
