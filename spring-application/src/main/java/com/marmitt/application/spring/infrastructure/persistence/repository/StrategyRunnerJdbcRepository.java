package com.marmitt.application.spring.infrastructure.persistence.repository;

import com.marmitt.application.spring.infrastructure.persistence.entity.StrategyRunnerEntity;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StrategyRunnerJdbcRepository extends CrudRepository<StrategyRunnerEntity, UUID> {

    @Query("SELECT * FROM strategy_runners WHERE portfolio_id = :portfolioId")
    List<StrategyRunnerEntity> findByPortfolioId(@Param("portfolioId") UUID portfolioId);

    @Query("SELECT * FROM strategy_runners WHERE portfolio_id = :portfolioId AND status IN ('ACTIVE', 'HALTED')")
    List<StrategyRunnerEntity> findOperationalByPortfolioId(@Param("portfolioId") UUID portfolioId);

    /**
     * Hot path — chamado em cada tick de preço.
     * JOIN necessário com portfolios para filtrar portfolios inativos.
     * Retorna apenas runners operacionais (ACTIVE ou HALTED) de portfolios ativos.
     */
    @Query("""
            SELECT sr.*
              FROM strategy_runners sr
             INNER JOIN portfolios p ON p.id = sr.portfolio_id
             WHERE UPPER(sr.symbol) = UPPER(:symbol)
               AND UPPER(sr.exchange_id) = UPPER(:exchangeId)
               AND sr.status IN ('ACTIVE', 'HALTED')
               AND p.is_active = true
            """)
    List<StrategyRunnerEntity> findOperationalBySymbolAndExchange(
            @Param("symbol") String symbol,
            @Param("exchangeId") String exchangeId
    );

    @Query("SELECT * FROM strategy_runners WHERE short_code = :shortCode AND portfolio_id = :portfolioId")
    Optional<StrategyRunnerEntity> findByShortCodeAndPortfolioId(
            @Param("shortCode") String shortCode,
            @Param("portfolioId") UUID portfolioId
    );
}
