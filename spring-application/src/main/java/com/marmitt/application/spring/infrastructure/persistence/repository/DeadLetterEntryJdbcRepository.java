package com.marmitt.application.spring.infrastructure.persistence.repository;

import com.marmitt.application.spring.infrastructure.persistence.entity.DeadLetterEntryEntity;
import com.marmitt.core.enums.DlqReason;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DeadLetterEntryJdbcRepository extends CrudRepository<DeadLetterEntryEntity, UUID> {

    @Query("""
            SELECT *
              FROM dead_letter_entries
             WHERE is_resolved = FALSE
               AND (:portfolioId IS NULL OR portfolio_id = :portfolioId)
               AND (:runnerId IS NULL OR runner_id = :runnerId)
             ORDER BY created_at DESC
             LIMIT :limit
            """)
    List<DeadLetterEntryEntity> findUnresolved(@Param("portfolioId") UUID portfolioId,
                                               @Param("runnerId") UUID runnerId,
                                               @Param("limit") int limit);

    @Query("""
            SELECT EXISTS(
                SELECT 1
                  FROM dead_letter_entries
                 WHERE portfolio_id = :portfolioId
                   AND is_resolved = FALSE
            )
            """)
    boolean existsUnresolvedByPortfolioId(@Param("portfolioId") UUID portfolioId);

    @Query("""
            SELECT EXISTS(
                SELECT 1
                  FROM dead_letter_entries
                 WHERE portfolio_id = :portfolioId
                   AND runner_id IS NULL
                   AND is_resolved = FALSE
            )
            """)
    boolean existsUnresolvedByPortfolioIdAndRunnerIsNull(@Param("portfolioId") UUID portfolioId);

    @Query("""
            SELECT EXISTS(
                SELECT 1
                  FROM dead_letter_entries
                 WHERE runner_id = :runnerId
                   AND is_resolved = FALSE
            )
            """)
    boolean existsUnresolvedByRunnerId(@Param("runnerId") UUID runnerId);

    @Query("""
            SELECT EXISTS(
                SELECT 1
                  FROM dead_letter_entries
                 WHERE portfolio_id = :portfolioId
                   AND (
                       (:runnerId IS NULL AND runner_id IS NULL)
                       OR runner_id = :runnerId
                   )
                   AND is_resolved = FALSE
                   AND reason = :reason
                   AND (
                       (:clientOrderId IS NULL AND client_order_id IS NULL)
                       OR client_order_id = :clientOrderId
                   )
                   AND (
                       (:exchangeOrderId IS NULL AND exchange_order_id IS NULL)
                       OR exchange_order_id = :exchangeOrderId
                   )
            )
            """)
    boolean existsUnresolvedByIdentity(@Param("portfolioId") UUID portfolioId,
                                       @Param("runnerId") UUID runnerId,
                                       @Param("clientOrderId") String clientOrderId,
                                       @Param("exchangeOrderId") String exchangeOrderId,
                                       @Param("reason") DlqReason reason);
}
