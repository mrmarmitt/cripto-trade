package com.marmitt.core.ports.inbound.reconciliation;

import com.marmitt.core.dto.reconciliation.ReconciliationReportDto;
import com.marmitt.core.dto.reconciliation.ReconciliationRequest;

public interface ReconcileTradesPort {

    ReconciliationReportDto reconcile(ReconciliationRequest request);
}
