package com.marmitt.application.spring.infrastructure.persistence.repository;

import com.marmitt.application.spring.infrastructure.persistence.entity.DeadLetterEntryEntity;
import com.marmitt.core.enums.DlqReason;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface DeadLetterEntryJdbcRepository extends CrudRepository<DeadLetterEntryEntity, UUID> {

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
                                       @Param("clientOrderId") String clientOrderId,
                                       @Param("exchangeOrderId") String exchangeOrderId,
                                       @Param("reason") DlqReason reason);
}
