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

    @Query("SELECT * FROM runner_positions WHERE runner_id = :runnerId AND status = 'OPEN'")
    List<RunnerPositionEntity> findOpenByRunnerId(@Param("runnerId") UUID runnerId);

    @Query("SELECT * FROM runner_positions WHERE runner_id = :runnerId AND UPPER(symbol) = UPPER(:symbol) AND status = 'OPEN'")
    Optional<RunnerPositionEntity> findOpenByRunnerIdAndSymbol(
            @Param("runnerId") UUID runnerId,
            @Param("symbol") String symbol
    );
}
