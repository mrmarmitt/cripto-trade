package com.marmitt.application.spring.controller;

import com.marmitt.application.spring.web.OperatorCommandInterceptor;

import com.marmitt.core.dto.portfolio.response.DeadLetterEntryDto;
import com.marmitt.core.dto.portfolio.response.ReprocessDeadLetterResponse;
import com.marmitt.core.dto.portfolio.response.ResolveDeadLetterResponse;
import com.marmitt.core.ports.inbound.portfolio.ManageDeadLetterPort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/dead-letters")
public class DeadLetterController {

    private final ManageDeadLetterPort manageDeadLetter;

    public DeadLetterController(ManageDeadLetterPort manageDeadLetter) {
        this.manageDeadLetter = manageDeadLetter;
    }

    @GetMapping
    public ResponseEntity<List<DeadLetterEntryDto>> listUnresolved(
            @RequestParam(required = false) UUID portfolioId,
            @RequestParam(required = false) UUID runnerId,
            @RequestParam(defaultValue = "100") int limit
    ) {
        List<DeadLetterEntryDto> entries = manageDeadLetter.listUnresolved(portfolioId, runnerId, limit);
        return ResponseEntity.ok(entries);
    }

    /**
     * Quem resolve vem de {@code X-Operator} e a nota de {@code X-Reason} (T55). Repetir em entrada ja
     * resolvida devolve o estado atual.
     */
    @PostMapping("/{id}/resolve")
    public ResponseEntity<ResolveDeadLetterResponse> resolve(
            @PathVariable UUID id,
            @RequestHeader(value = OperatorCommandInterceptor.OPERATOR, defaultValue = "unknown") String operator,
            @RequestHeader(OperatorCommandInterceptor.REASON) String reason
    ) {
        return ResponseEntity.ok(manageDeadLetter.resolve(id, operator, reason));
    }

    @PostMapping("/{id}/reprocess")
    public ResponseEntity<ReprocessDeadLetterResponse> reprocess(
            @PathVariable UUID id,
            @RequestHeader(value = OperatorCommandInterceptor.OPERATOR, defaultValue = "unknown") String operator,
            @RequestHeader(OperatorCommandInterceptor.REASON) String reason
    ) {
        return ResponseEntity.ok(manageDeadLetter.reprocess(id, operator, reason));
    }
}

