package com.marmitt.application.spring.controller;

import com.marmitt.binance.filters.SymbolFilterCache;
import com.marmitt.core.dto.runner.response.RunnerDto;
import com.marmitt.core.dto.runner.response.RunnerHaltResult;
import com.marmitt.core.ports.inbound.runner.HaltRunnerPort;
import com.marmitt.core.ports.inbound.runner.QueryRunnerPort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final List<SymbolFilterCache> filterCaches;
    private final HaltRunnerPort haltRunner;
    private final QueryRunnerPort queryRunner;

    public AdminController(List<SymbolFilterCache> filterCaches,
                           HaltRunnerPort haltRunner,
                           QueryRunnerPort queryRunner) {
        this.filterCaches = filterCaches;
        this.haltRunner = haltRunner;
        this.queryRunner = queryRunner;
    }

    @PostMapping("/filters/refresh")
    public ResponseEntity<Void> refreshFilters() {
        filterCaches.forEach(SymbolFilterCache::invalidateAll);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/runners/halt")
    public ResponseEntity<List<RunnerHaltResult>> haltAll() {
        return ResponseEntity.ok(haltRunner.haltAll());
    }

    @PostMapping("/runners/{id}/halt")
    public ResponseEntity<RunnerHaltResult> haltById(@PathVariable UUID id) {
        return ResponseEntity.ok(haltRunner.haltById(id));
    }

    @GetMapping("/runners/status")
    public ResponseEntity<List<RunnerDto>> runnersStatus() {
        return ResponseEntity.ok(queryRunner.findAll());
    }
}
