package com.marmitt.application.spring.controller;

import com.marmitt.core.dto.operations.OperationalStateReport;
import com.marmitt.core.ports.inbound.operations.InspectOperationalStatePort;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Estado operacional consolidado (T55). Só leitura; as verificações e os limiares são do core.
 */
@RestController
@RequestMapping("/api/admin/operational-state")
@Tag(name = "Operational state", description = """
        One read that says whether the system is consistent and, if not, what, where and what to do.""")
public class OperationalStateController {

    private final InspectOperationalStatePort inspectOperationalState;

    public OperationalStateController(InspectOperationalStatePort inspectOperationalState) {
        this.inspectOperationalState = inspectOperationalState;
    }

    @GetMapping
    @Operation(summary = "Consolidated operational state", description = """
            `status` is the worst severity among the checks (OK, WARN, CRITICAL). Every check is listed, OK
            ones included, with a stable `id`, the affected `subjects`, `since` and the `runbook` section with
            the diagnosis and the action. Check ids: BOOT_NOT_READY, SAFE_MODE_ACTIVE,
            CAPITAL_RESERVED_WITHOUT_ORDER, CAPITAL_RESERVATION_STALE, TRANSACTION_STUCK, POSITION_LOCK_ORPHAN,
            DLQ_UNRESOLVED, RUNNER_HALTED, RUNNER_RECONCILING, RUNNER_STUCK_TERMINATING,
            RUNNER_ON_HOLD_STRATEGY_UNUSABLE, STRATEGY_VERIFICATION_FAILED, MARKET_DATA_STALE,
            STREAM_DISCONNECTED, EXCHANGE_REST_CIRCUIT_OPEN. Also exported as the metrics
            operational_state_status and operational_check_status{check} (0=OK, 1=WARN, 2=CRITICAL).""")
    public ResponseEntity<OperationalStateReport> inspect() {
        return ResponseEntity.ok(inspectOperationalState.inspect());
    }
}
