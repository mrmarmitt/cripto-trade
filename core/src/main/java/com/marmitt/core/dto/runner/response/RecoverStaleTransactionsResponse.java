package com.marmitt.core.dto.runner.response;

public record RecoverStaleTransactionsResponse(
        int scanned,
        int recovered,
        int routedToDlq,
        int skipped,
        int failed
) {
}
