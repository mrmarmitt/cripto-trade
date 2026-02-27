package com.marmitt.application.spring.controller;

import com.marmitt.application.spring.controller.dto.portfolio.CreatePortfolioDto;
import com.marmitt.core.dto.portfolio.CreatePortfolioRequest;
import com.marmitt.core.dto.portfolio.CreatePortfolioResponse;
import com.marmitt.core.dto.portfolio.AggregatedTransactionsResponse;
import com.marmitt.core.dto.portfolio.GlobalBalanceDto;
import com.marmitt.core.dto.portfolio.PortfolioDto;
import com.marmitt.core.dto.portfolio.PortfolioPnlDto;
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
     * Retorna o GlobalBalance de um portfolio
     * GET /portfolios/{id}/balance
     */
    @GetMapping("/{id}/balance")
    public ResponseEntity<GlobalBalanceDto> getBalanceByPortfolioId(@PathVariable UUID id) {
        log.debug("Received request to get balance for portfolio: {}", id);

        Optional<GlobalBalanceDto> balanceOpt = queryPortfolio.findBalanceByPortfolioId(id);
        if (balanceOpt.isEmpty()) {
            log.warn("GlobalBalance not found for portfolio: {}", id);
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(balanceOpt.get());
    }

    /**
     * Retorna o PnL consolidado de um portfolio
     * GET /portfolios/{id}/pnl
     */
    @GetMapping("/{id}/pnl")
    public ResponseEntity<PortfolioPnlDto> getPnlByPortfolioId(@PathVariable UUID id) {
        log.debug("Received request to get PnL for portfolio: {}", id);

        Optional<PortfolioPnlDto> pnlOpt = queryPortfolio.findPnlByPortfolioId(id);
        if (pnlOpt.isEmpty()) {
            log.warn("PnL not found for portfolio: {}", id);
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(pnlOpt.get());
    }

}
