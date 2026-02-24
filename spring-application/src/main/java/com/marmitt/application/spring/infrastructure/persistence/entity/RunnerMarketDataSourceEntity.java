package com.marmitt.application.spring.infrastructure.persistence.entity;

import org.springframework.data.relational.core.mapping.Table;

@Table("runner_market_data_sources")
public record RunnerMarketDataSourceEntity(String source) {
}
