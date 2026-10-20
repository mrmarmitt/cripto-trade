package com.marmitt.application.spring.controller;

import com.marmitt.application.spring.web.OperatorActionLog;
import com.marmitt.application.spring.web.OperatorActionLog.OperatorAction;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Consulta do log de acoes do operador (T55).
 */
@RestController
@RequestMapping("/api/admin/operator-actions")
@Tag(name = "Operator actions", description = """
        Every command sent to the API (POST, PUT, PATCH, DELETE) with who sent it (X-Operator), why (X-Reason),
        the handler, the HTTP status and the refusal `code`. Refused commands are recorded too.""")
public class OperatorActionController {

    private static final int MAX_LIMIT = 1000;

    private final OperatorActionLog actionLog;

    public OperatorActionController(OperatorActionLog actionLog) {
        this.actionLog = actionLog;
    }

    @GetMapping
    @Operation(summary = "List operator actions", description = """
            Most recent first. `since` defaults to 24 hours ago; `pathContains` filters by a fragment of the
            request path (for example a runner or strategy id); `limit` is capped at 1000.""")
    public ResponseEntity<List<OperatorAction>> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant since,
            @RequestParam(required = false) String pathContains,
            @RequestParam(defaultValue = "100") int limit) {
        Instant from = since != null ? since : Instant.now().minus(Duration.ofHours(24));
        return ResponseEntity.ok(actionLog.find(from, pathContains, Math.max(1, Math.min(limit, MAX_LIMIT))));
    }
}
