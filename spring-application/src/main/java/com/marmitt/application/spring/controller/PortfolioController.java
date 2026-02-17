package com.marmitt.application.spring.controller;

import com.marmitt.application.spring.controller.dto.portfolio.CreatePortfolioDto;
import com.marmitt.core.dto.portfolio.CreatePortfolioRequest;
import com.marmitt.core.dto.portfolio.CreatePortfolioResponse;
import com.marmitt.core.dto.portfolio.PortfolioDto;
import com.marmitt.core.dto.portfolio.TransactionDto;
import com.marmitt.core.ports.inbound.portfolio.CreatePortfolioPort;
import com.marmitt.core.ports.inbound.portfolio.QueryPortfolioPort;
import jakarta.validation.Valid;
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
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/portfolios")
public class PortfolioController {

    private final CreatePortfolioPort createPortfolio;
    private final QueryPortfolioPort queryPortfolio;

    public PortfolioController(
            CreatePortfolioPort createPortfolio,
            QueryPortfolioPort queryPortfolio
    ) {
        this.createPortfolio = createPortfolio;
        this.queryPortfolio = queryPortfolio;
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
            CreatePortfolioRequest request = CreatePortfolioRequest.builder()
                    .name(dto.name())
                    .strategyId(dto.strategyId())
                    .symbol(dto.symbol())
                    .initialCapitalAmount(dto.initialCapitalAmount())
                    .currency(dto.currency())
                    .exchangeName(dto.orderExecutionExchange())
                    .allowedMarketDataSources(dto.allowedMarketDataSources())
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
     * Busca portfolios por símbolo
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
     * Busca portfolio por nome
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
     * Lista transações de um portfolio
     * GET /portfolios/{id}/transactions
     */
    @GetMapping("/{id}/transactions")
    public ResponseEntity<TransactionsResponse> getTransactionsByPortfolioId(@PathVariable UUID id) {
        log.debug("Received request to get transactions for portfolio: {}", id);

        Optional<PortfolioDto> portfolioOpt = queryPortfolio.findById(id);

        if (portfolioOpt.isEmpty()) {
            log.warn("Portfolio not found with ID: {}", id);
            return ResponseEntity.notFound().build();
        }

        PortfolioDto portfolio = portfolioOpt.get();
        List<TransactionDto> transactions = queryPortfolio.findTransactionsByPortfolioId(id);

        TransactionsResponse response = new TransactionsResponse(
                portfolio.id(),
                portfolio.name(),
                transactions,
                transactions.size()
        );

        log.debug("Found {} transactions for portfolio: {}", transactions.size(), id);
        return ResponseEntity.ok(response);
    }

    public record TransactionsResponse(
            UUID portfolioId,
            String portfolioName,
            List<TransactionDto> transactions,
            int totalCount
    ) {}
}
