package com.marmitt.core.dto.portfolio;

import com.marmitt.core.domain.portfolio.Portfolio;
import com.marmitt.core.enums.CapitalPoolingMode;
import com.marmitt.core.enums.SafeModeStatus;
import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record PortfolioDto(
        UUID id,
        String name,
        boolean isActive,
        Instant createdAt,
        SafeModeStatus safeModeStatus,
        CapitalPoolingMode capitalPoolingMode
) {

    public static PortfolioDto fromDomain(Portfolio portfolio) {
        return PortfolioDto.builder()
                .id(portfolio.getId())
                .name(portfolio.getName())
                .isActive(portfolio.isActive())
                .createdAt(portfolio.getCreatedAt())
                .safeModeStatus(portfolio.getSafeModeStatus())
                .capitalPoolingMode(portfolio.getCapitalPoolingMode())
                .build();
    }
}
