package com.marmitt.application.spring.infrastructure.persistence.entity;

import org.springframework.data.relational.core.mapping.Table;

@Table("portfolio_market_data_sources")
public record MarketDataSourceRef(String source) {
}
