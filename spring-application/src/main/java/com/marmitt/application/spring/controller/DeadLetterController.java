package com.marmitt.application.spring.controller;

import com.marmitt.core.dto.portfolio.DeadLetterEntryDto;
import com.marmitt.core.dto.portfolio.ReprocessDeadLetterRequest;
import com.marmitt.core.dto.portfolio.ReprocessDeadLetterResponse;
import com.marmitt.core.dto.portfolio.ResolveDeadLetterRequest;
import com.marmitt.core.dto.portfolio.ResolveDeadLetterResponse;
import com.marmitt.core.ports.inbound.portfolio.ManageDeadLetterPort;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
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

@Slf4j
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
        try {
            ResolveDeadLetterResponse response = manageDeadLetter.resolve(
                    id,
                    request.resolvedBy(),
                    request.resolutionNote()
            );

            if (response.resolved()) {
                return ResponseEntity.ok(response);
            }
            if (response.message() != null && response.message().toLowerCase().contains("not found")) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        } catch (IllegalArgumentException e) {
            log.warn("deadLetter: invalid resolve request id={} reason={}", id, e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ResolveDeadLetterResponse.failure(id, "Invalid request: " + e.getMessage()));
        } catch (Exception e) {
            log.error("deadLetter: unexpected error resolving id={}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ResolveDeadLetterResponse.failure(id, "Internal server error: " + e.getMessage()));
        }
    }

    @PostMapping("/{id}/reprocess")
    public ResponseEntity<ReprocessDeadLetterResponse> reprocess(
            @PathVariable UUID id,
            @Valid @RequestBody ReprocessDeadLetterRequest request
    ) {
        try {
            ReprocessDeadLetterResponse response = manageDeadLetter.reprocess(
                    id,
                    request.requestedBy(),
                    request.resolutionNote()
            );

            if (response.reprocessed()) {
                return ResponseEntity.ok(response);
            }
            if (response.message() != null && response.message().toLowerCase().contains("not found")) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        } catch (IllegalArgumentException e) {
            log.warn("deadLetter: invalid reprocess request id={} reason={}", id, e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ReprocessDeadLetterResponse.failure(id, "Invalid request: " + e.getMessage()));
        } catch (Exception e) {
            log.error("deadLetter: unexpected error reprocessing id={}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ReprocessDeadLetterResponse.failure(id, "Internal server error: " + e.getMessage()));
        }
    }
}
