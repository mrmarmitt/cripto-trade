package com.marmitt.binance.filters;

import java.math.BigDecimal;

record SymbolFilters(
        BigDecimal stepSize,
        BigDecimal minQty,
        BigDecimal maxQty,
        BigDecimal tickSize,
        BigDecimal minNotional
) {}
