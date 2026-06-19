package com.marmitt.application.spring.controller;

import com.marmitt.core.dto.portfolio.response.DeadLetterEntryDto;
import com.marmitt.core.dto.portfolio.request.ReprocessDeadLetterRequest;
import com.marmitt.core.dto.portfolio.response.ReprocessDeadLetterResponse;
import com.marmitt.core.dto.portfolio.request.ResolveDeadLetterRequest;
import com.marmitt.core.dto.portfolio.response.ResolveDeadLetterResponse;
import com.marmitt.core.ports.inbound.portfolio.ManageDeadLetterPort;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    @PostMapping("/{id}/resolve")
    public ResponseEntity<ResolveDeadLetterResponse> resolve(
            @PathVariable UUID id,
            @Valid @RequestBody ResolveDeadLetterRequest request
    ) {
        ResolveDeadLetterResponse response = manageDeadLetter.resolve(
                id, request.resolvedBy(), request.resolutionNote());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/reprocess")
    public ResponseEntity<ReprocessDeadLetterResponse> reprocess(
            @PathVariable UUID id,
            @Valid @RequestBody ReprocessDeadLetterRequest request
    ) {
        ReprocessDeadLetterResponse response = manageDeadLetter.reprocess(
                id, request.requestedBy(), request.resolutionNote());
        return ResponseEntity.ok(response);
    }
}

