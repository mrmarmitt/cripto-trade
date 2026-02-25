package com.marmitt.application.spring.controller;

import com.marmitt.core.dto.portfolio.TransactionDto;
import com.marmitt.core.ports.inbound.runner.QueryRunnerPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/runners")
public class RunnerController {

    private final QueryRunnerPort queryRunner;

    public RunnerController(QueryRunnerPort queryRunner) {
        this.queryRunner = queryRunner;
    }

    /**
     * Lista transacoes de um runner
     * GET /runners/{id}/transactions
     */
    @GetMapping("/{id}/transactions")
    public ResponseEntity<List<TransactionDto>> getTransactionsByRunnerId(@PathVariable UUID id) {
        log.debug("Received request to get transactions for runner: {}", id);

        List<TransactionDto> transactions = queryRunner.findTransactionsByRunnerId(id);
        log.debug("Found {} transaction(s) for runner: {}", transactions.size(), id);
        return ResponseEntity.ok(transactions);
    }
}
