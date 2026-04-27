package com.marmitt.application.spring.controller;

import com.marmitt.core.dto.portfolio.response.PortfolioDto;
import com.marmitt.core.dto.runner.request.CreateRunnerDto;
import com.marmitt.core.dto.runner.request.CreateRunnerRequest;
import com.marmitt.core.dto.runner.response.CreateRunnerResponse;
import com.marmitt.core.dto.runner.response.RunnerDto;
import com.marmitt.core.ports.inbound.portfolio.QueryPortfolioPort;
import com.marmitt.core.ports.inbound.runner.CreateRunnerPort;
import com.marmitt.core.ports.inbound.runner.QueryRunnerPort;
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
public class PortfolioRunnerController {

    private final CreateRunnerPort createRunner;
    private final QueryRunnerPort queryRunner;
    private final QueryPortfolioPort queryPortfolio;

    public PortfolioRunnerController(
            CreateRunnerPort createRunner,
            QueryRunnerPort queryRunner,
            QueryPortfolioPort queryPortfolio
    ) {
        this.createRunner = createRunner;
        this.queryRunner = queryRunner;
        this.queryPortfolio = queryPortfolio;
    }

    /**
     * Cria um novo runner para um portfolio existente
     * POST /portfolios/{id}/runners
     */
    @PostMapping("/{id}/runners")
    public ResponseEntity<CreateRunnerResponse> createRunner(
            @PathVariable UUID id,
            @RequestBody CreateRunnerDto dto
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
}


