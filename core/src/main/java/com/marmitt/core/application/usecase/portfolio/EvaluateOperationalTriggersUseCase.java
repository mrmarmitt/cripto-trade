package com.marmitt.core.application.usecase.portfolio;

import com.marmitt.core.domain.portfolio.Portfolio;
import com.marmitt.core.enums.SafeModeStatus;
import com.marmitt.core.enums.SafeModeTrigger;
import com.marmitt.core.ports.inbound.portfolio.EvaluateOperationalTriggersPort;
import com.marmitt.core.ports.outbound.repository.DeadLetterEntryRepositoryPort;
import com.marmitt.core.ports.outbound.repository.PortfolioRepositoryPort;
import lombok.extern.slf4j.Slf4j;

/**
 * Gatilho de DLQ (T25): portfolio em NORMAL com {@code dlqThreshold} ou mais entradas não resolvidas
 * entra em HALT pelo {@link AutomaticSafeModeTrigger}. DLQ acumulando indica conciliação falhando, com
 * estado local possivelmente inconsistente.
 */
@Slf4j
public class EvaluateOperationalTriggersUseCase implements EvaluateOperationalTriggersPort {

    private final PortfolioRepositoryPort portfolioRepository;
    private final DeadLetterEntryRepositoryPort deadLetterEntryRepository;
    private final AutomaticSafeModeTrigger safeModeTrigger;
    private final int dlqThreshold;

    public EvaluateOperationalTriggersUseCase(PortfolioRepositoryPort portfolioRepository,
                                              DeadLetterEntryRepositoryPort deadLetterEntryRepository,
                                              AutomaticSafeModeTrigger safeModeTrigger,
                                              int dlqThreshold) {
        if (dlqThreshold <= 0) {
            throw new IllegalArgumentException("dlqThreshold must be positive: " + dlqThreshold);
        }
        this.portfolioRepository = portfolioRepository;
        this.deadLetterEntryRepository = deadLetterEntryRepository;
        this.safeModeTrigger = safeModeTrigger;
        this.dlqThreshold = dlqThreshold;
    }

    @Override
    public void evaluate() {
        for (Portfolio portfolio : portfolioRepository.findAll()) {
            if (!portfolio.isActive() || portfolio.getSafeModeStatus() != SafeModeStatus.NORMAL) {
                continue;
            }
            try {
                long unresolved = deadLetterEntryRepository.countUnresolvedByPortfolioId(portfolio.getId());
                if (unresolved >= dlqThreshold) {
                    safeModeTrigger.activate(portfolio.getId(), SafeModeTrigger.DLQ_THRESHOLD,
                            "unresolved=" + unresolved + " threshold=" + dlqThreshold);
                }
            } catch (RuntimeException e) {
                log.error("autoSafeMode: DLQ evaluation failed portfolioId={} - {}",
                        portfolio.getId(), e.getMessage(), e);
            }
        }
    }
}
