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

    /**
     * Finds portfolios that have at least one operational runner for the given symbol.
     * Delegates symbol lookup to strategy_runners (symbol moved from Portfolio to StrategyRunner).
     */
    @Query("""
            SELECT * FROM portfolios
             WHERE id IN (
                   SELECT portfolio_id FROM strategy_runners
                    WHERE UPPER(symbol) = UPPER(:symbol)
                      AND status NOT IN ('ARCHIVED', 'TERMINATING')
             )
            """)
    List<PortfolioEntity> findBySymbol(@Param("symbol") String symbol);

    @Query("SELECT * FROM portfolios WHERE is_active = true")
    List<PortfolioEntity> findByIsActiveTrue();
}
