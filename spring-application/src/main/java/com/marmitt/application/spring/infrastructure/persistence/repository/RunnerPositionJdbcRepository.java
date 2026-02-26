package com.marmitt.application.spring.infrastructure.persistence.repository;

import com.marmitt.application.spring.infrastructure.persistence.entity.RunnerPositionEntity;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RunnerPositionJdbcRepository extends CrudRepository<RunnerPositionEntity, UUID> {

    @Query("SELECT * FROM positions WHERE runner_id = :runnerId AND status = 'OPEN'")
    List<RunnerPositionEntity> findOpenByRunnerId(@Param("runnerId") UUID runnerId);

    @Query("SELECT * FROM positions WHERE runner_id = :runnerId AND UPPER(symbol) = UPPER(:symbol) AND status = 'OPEN' AND locked_by_transaction_id IS NULL")
    Optional<RunnerPositionEntity> findOpenByRunnerIdAndSymbol(
            @Param("runnerId") UUID runnerId,
            @Param("symbol") String symbol
    );

    @org.springframework.data.jdbc.repository.query.Modifying
    @Query("""
            UPDATE positions
               SET locked_by_transaction_id = :transactionId,
                   locked_quantity = :quantity,
                   locked_at = NOW(),
                   status = 'CLOSING',
                   updated_at = NOW(),
                   version = version + 1
             WHERE id = :positionId
               AND status = 'OPEN'
               AND locked_by_transaction_id IS NULL
            """)
    int tryLockPositionForSell(
            @Param("positionId") UUID positionId,
            @Param("transactionId") UUID transactionId,
            @Param("quantity") java.math.BigDecimal quantity
    );
}
