package com.marmitt.core.exceptions;

import java.util.UUID;

public class PortfolioNotFoundException extends RuntimeException implements CodedError {

    public PortfolioNotFoundException(UUID portfolioId) {
        super("Portfolio not found: " + portfolioId);
    }

    @Override
    public String code() {
        return "PORTFOLIO_NOT_FOUND";
    }
}
