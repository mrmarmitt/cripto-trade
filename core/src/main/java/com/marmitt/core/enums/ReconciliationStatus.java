package com.marmitt.core.enums;

public enum ReconciliationStatus {
    /** Exists on both sides; qty and value within tolerance. */
    MATCHED,
    /** Exists on both sides but qty or total value diverges beyond tolerance. */
    DIVERGENT,
    /** Trade executed on exchange with no corresponding local transaction. */
    EXCHANGE_ONLY,
    /** Local transaction (FILLED or PARTIAL) with no exchange trade confirming it. */
    LOCAL_ONLY
}
