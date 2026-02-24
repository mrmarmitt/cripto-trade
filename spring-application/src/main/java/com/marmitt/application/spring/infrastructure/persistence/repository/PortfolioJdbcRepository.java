package com.marmitt.application.spring.infrastructure.persistence.repository;

import com.marmitt.application.spring.infrastructure.persistence.entity.PortfolioEntity;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PortfolioJdbcRepository extends CrudRepository<PortfolioEntity, UUID> {

    @Query("SELECT * FROM portfolios WHERE LOWER(name) = LOWER(:name)")
    Optional<PortfolioEntity> findByNameIgnoreCase(@Param("name") String name);

    @Query("SELECT * FROM portfolios WHERE UPPER(symbol) = UPPER(:symbol)")
    List<PortfolioEntity> findBySymbolIgnoreCase(@Param("symbol") String symbol);

    @Query("SELECT * FROM portfolios WHERE UPPER(symbol) = UPPER(:symbol) AND strategy_id = :strategyId")
    Optional<PortfolioEntity> findBySymbolAndStrategyId(
            @Param("symbol") String symbol,
            @Param("strategyId") UUID strategyId
    );

    @Query("SELECT * FROM portfolios WHERE is_active = true")
    List<PortfolioEntity> findByIsActiveTrue();
}
