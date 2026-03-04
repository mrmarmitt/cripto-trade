package com.marmitt.application.spring.infrastructure.persistence.adapter;

import com.marmitt.application.spring.infrastructure.persistence.mapper.DeadLetterEntryEntityMapper;
import com.marmitt.application.spring.infrastructure.persistence.repository.DeadLetterEntryJdbcRepository;
import com.marmitt.core.domain.portfolio.DeadLetterEntry;
import com.marmitt.core.ports.outbound.repository.DeadLetterEntryRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Repository
@RequiredArgsConstructor
public class JdbcDeadLetterEntryRepositoryAdapter implements DeadLetterEntryRepositoryPort {

    private final DeadLetterEntryJdbcRepository deadLetterEntryJdbcRepository;

    @Override
    @Transactional
    public void save(DeadLetterEntry entry) {
        long start = System.nanoTime();
        deadLetterEntryJdbcRepository.save(DeadLetterEntryEntityMapper.toEntity(entry));
        log.trace("[REPO] deadLetterEntry.save({}) - {}ms", entry.getId(), RepoTiming.elapsedMs(start));
    }
}
