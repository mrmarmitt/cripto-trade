package com.marmitt.application.spring.controller;

import com.marmitt.core.dto.reconciliation.ReconciliationReportDto;
import com.marmitt.core.dto.reconciliation.ReconciliationRequest;
import com.marmitt.core.ports.inbound.reconciliation.ReconcileTradesPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@Slf4j
@RestController
@RequestMapping("/api/reconciliation")
@ConditionalOnExpression("!'${binance.api-key:}'.isBlank() && !'${binance.api-secret:}'.isBlank()")
public class ReconciliationController {

    private final ReconcileTradesPort reconcileTradesPort;

    public ReconciliationController(ReconcileTradesPort reconcileTradesPort) {
        this.reconcileTradesPort = reconcileTradesPort;
    }

    /**
     * GET /api/reconciliation?symbol=BTCUSDT&exchangeId=BINANCE&from=2025-01-01T00:00:00Z&to=2025-01-02T00:00:00Z
     *
     * @param includeMatched when false (default), MATCHED entries are omitted from the entries list
     *                       but still counted in summary.matched
     */
    @GetMapping
    public ResponseEntity<?> reconcile(
            @RequestParam String symbol,
            @RequestParam(defaultValue = "BINANCE") String exchangeId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "false") boolean includeMatched) {

        if (from.isAfter(to)) {
            return ResponseEntity.badRequest().body("'from' must be before 'to'");
        }

        log.info("reconciliation: symbol={} exchangeId={} from={} to={} includeMatched={}",
                symbol, exchangeId, from, to, includeMatched);

        try {
            ReconciliationReportDto report = reconcileTradesPort.reconcile(
                    new ReconciliationRequest(symbol, exchangeId, from, to, includeMatched));
            log.info("reconciliation: done symbol={} total={} matched={} divergent={} exchangeOnly={} localOnly={}",
                    symbol,
                    report.summary().total(),
                    report.summary().matched(),
                    report.summary().divergent(),
                    report.summary().exchangeOnly(),
                    report.summary().localOnly());
            return ResponseEntity.ok(report);
        } catch (IllegalArgumentException e) {
            log.warn("reconciliation: bad request symbol={} error={}", symbol, e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (RuntimeException e) {
            log.error("reconciliation: failed symbol={} error={}", symbol, e.getMessage(), e);
            return ResponseEntity.status(502).body("Exchange query failed: " + e.getMessage());
        }
    }
}
