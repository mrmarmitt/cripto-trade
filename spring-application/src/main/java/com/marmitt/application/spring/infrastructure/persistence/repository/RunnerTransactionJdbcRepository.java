package com.marmitt.application.spring.infrastructure.persistence.repository;

import com.marmitt.application.spring.infrastructure.persistence.entity.RunnerTransactionEntity;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.time.Instant;
import java.util.UUID;

@Repository
public interface RunnerTransactionJdbcRepository extends CrudRepository<RunnerTransactionEntity, UUID> {

    @Query("SELECT * FROM transactions WHERE client_order_id = :clientOrderId")
    Optional<RunnerTransactionEntity> findByClientOrderId(@Param("clientOrderId") String clientOrderId);

    /**
     * Busca transações de um Runner nos status informados.
     * {@code statuses} deve conter os nomes dos enums em String (ex: "PENDING", "SUBMITTED").
     */
    @Query("SELECT * FROM transactions WHERE runner_id = :runnerId AND status IN (:statuses) ORDER BY requested_at")
    List<RunnerTransactionEntity> findByRunnerIdAndStatuses(
            @Param("runnerId") UUID runnerId,
            @Param("statuses") Collection<String> statuses
    );

    @Query("SELECT * FROM transactions WHERE status IN (:statuses) AND updated_at < :updatedBefore ORDER BY updated_at LIMIT :limit")
    List<RunnerTransactionEntity> findByStatusesUpdatedBefore(
            @Param("statuses") Collection<String> statuses,
            @Param("updatedBefore") Instant updatedBefore,
            @Param("limit") int limit
    );

    @Query("""
            SELECT t.* FROM transactions t
            JOIN strategy_runners sr ON sr.id = t.runner_id
            WHERE t.symbol = :symbol
              AND UPPER(sr.exchange_id) = UPPER(:exchangeId)
              AND (
                (t.status IN ('FILLED', 'PARTIAL')
                 AND COALESCE(t.executed_at, t.updated_at) >= :from
                 AND COALESCE(t.executed_at, t.updated_at) <= :to)
                OR
                (t.status = 'CANCELED' AND t.executed_quantity > 0
                 AND t.last_partial_fill_at >= :from
                 AND t.last_partial_fill_at <= :to)
              )
            ORDER BY COALESCE(t.executed_at, t.last_partial_fill_at, t.updated_at)
            """)
    List<RunnerTransactionEntity> findFilledBySymbolAndPeriod(
            @Param("symbol") String symbol,
            @Param("exchangeId") String exchangeId,
            @Param("from") Instant from,
            @Param("to") Instant to
    );
}
