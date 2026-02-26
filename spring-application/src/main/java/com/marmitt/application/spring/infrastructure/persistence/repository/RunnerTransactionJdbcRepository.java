package com.marmitt.application.spring.infrastructure.persistence.repository;

import com.marmitt.application.spring.infrastructure.persistence.entity.RunnerTransactionEntity;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
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
}
