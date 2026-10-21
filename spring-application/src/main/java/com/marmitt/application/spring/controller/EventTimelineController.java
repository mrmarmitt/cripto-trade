package com.marmitt.application.spring.controller;

import com.marmitt.core.dto.operations.EventTimeline;
import com.marmitt.core.ports.inbound.operations.QueryEventTimelinePort;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

/**
 * Linha do tempo de eventos (T59). Só leitura; as fontes e os limites são do core.
 */
@RestController
@RequestMapping("/api/admin/events")
@Tag(name = "Events", description = """
        What happened, when and why: system and operator events in chronological order, read from the tables
        that record each of them.""")
public class EventTimelineController {

    private final QueryEventTimelinePort queryEventTimeline;

    public EventTimelineController(QueryEventTimelinePort queryEventTimeline) {
        this.queryEventTimeline = queryEventTimeline;
    }

    @GetMapping
    @Operation(summary = "Event timeline", description = """
            Oldest first. `since` is inclusive (default: 24 hours before `until`), `until` is exclusive (default:
            now); `limit` defaults to 200, capped at 1000. Types: SAFE_MODE_CHANGED, RUNNER_STATUS_CHANGED,
            PROTECTIVE_EXIT, DLQ_ENTRY, OPERATOR_ACTION, STRATEGY_CATALOG_CHANGED, BOOT_COMPLETED, BOOT_FAILED.
            `origin` is MANUAL, AUTOMATIC or BOOT. With `portfolioId`, only the types tied to a portfolio
            (Safe Mode, runner, protective exit, DLQ). When `truncated` is true, ask again with
            `since=nextSince`; events at that same instant may repeat and are deduplicated by `id`.""")
    public ResponseEntity<EventTimeline> find(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant since,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant until,
            @RequestParam(required = false) UUID portfolioId,
            @RequestParam(required = false) Integer limit) {
        return ResponseEntity.ok(queryEventTimeline.find(since, until, portfolioId, limit));
    }
}
