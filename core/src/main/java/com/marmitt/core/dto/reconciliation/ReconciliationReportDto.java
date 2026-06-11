package com.marmitt.core.dto.reconciliation;

import java.time.Instant;
import java.util.List;

public record ReconciliationReportDto(
        String symbol,
        Instant from,
        Instant to,
        ReconciliationSummaryDto summary,
        List<ReconciliationEntryDto> entries
) {}
