package com.marmitt.application.spring.bootstrap;

import com.marmitt.core.application.usecase.portfolio.PortfolioBootSanityUseCase;
import com.marmitt.core.application.usecase.portfolio.PortfolioReservationTtlUseCase;
import com.marmitt.core.application.usecase.portfolio.PortfolioZombieDetectionUseCase;
import com.marmitt.core.application.usecase.runner.RunnerBootRecoveryUseCase;
import com.marmitt.core.domain.portfolio.Portfolio;
import com.marmitt.core.domain.runner.StrategyRunner;
import com.marmitt.core.dto.portfolio.PortfolioZombieDetectionResult;
import com.marmitt.core.enums.AccountingPolicyType;
import com.marmitt.core.enums.ExecutionPolicy;
import com.marmitt.core.enums.RunnerStatus;
import com.marmitt.core.ports.outbound.repository.DeadLetterEntryRepositoryPort;
import com.marmitt.core.ports.outbound.repository.ExchangeAdapterRepositoryPort;
import com.marmitt.core.ports.outbound.repository.PortfolioRepositoryPort;
import com.marmitt.core.ports.outbound.repository.StrategyRunnerRepositoryPort;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BootOrchestratorBootMinimumTest {

    @Test
    void warnOnlyZombieDetectionDoesNotPersistDlqAndRunCompletes() {
        Portfolio portfolio = new Portfolio(UUID.randomUUID(), "p1");
        StrategyRunner runner = activeRunner(portfolio.getId(), "MOCK");
        PortfolioZombieDetectionResult detected = PortfolioZombieDetectionResult.detected(
                portfolio.getId(), "MOCK", 1, 1, 0, 0, 0, 0, List.of()
        );

        BootStatusTracker tracker = new BootStatusTracker();
        DeadLetterEntryRepositoryPort deadLetterRepository = mock(DeadLetterEntryRepositoryPort.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        BootOrchestrator orchestrator = newOrchestrator(
                Phase2Mode.WARN_ONLY,
                portfolio,
                runner,
                detected,
                tracker,
                deadLetterRepository,
                eventPublisher
        );

        orchestrator.onApplicationReady();

        BootRunSnapshot snapshot = tracker.snapshot();
        assertEquals(BootRunStatus.SUCCESS, snapshot.status());
        assertNull(snapshot.failurePhase());
        verifyNoInteractions(deadLetterRepository);
        verify(eventPublisher, never()).publishEvent(any(BootFailFastEvent.class));
    }

    @Test
    void failFastZombieDetectionPreservesPhaseSpecificFailureMetadata() {
        Portfolio portfolio = new Portfolio(UUID.randomUUID(), "p1");
        StrategyRunner runner = activeRunner(portfolio.getId(), "MOCK");
        PortfolioZombieDetectionResult detected = PortfolioZombieDetectionResult.detected(
                portfolio.getId(), "MOCK", 1, 1, 0, 0, 0, 0, List.of()
        );

        BootStatusTracker tracker = new BootStatusTracker();
        DeadLetterEntryRepositoryPort deadLetterRepository = mock(DeadLetterEntryRepositoryPort.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        BootOrchestrator orchestrator = newOrchestrator(
                Phase2Mode.FAIL_FAST,
                portfolio,
                runner,
                detected,
                tracker,
                deadLetterRepository,
                eventPublisher
        );

        assertThrows(IllegalStateException.class, orchestrator::onApplicationReady);

        BootRunSnapshot snapshot = tracker.snapshot();
        assertEquals(BootRunStatus.FAILED, snapshot.status());
        assertEquals("phase2.zombie", snapshot.failurePhase());
        verify(eventPublisher).publishEvent(any(BootFailFastEvent.class));
    }

    private static BootOrchestrator newOrchestrator(Phase2Mode mode,
                                                    Portfolio portfolio,
                                                    StrategyRunner runner,
                                                    PortfolioZombieDetectionResult zombieResult,
                                                    BootStatusTracker tracker,
                                                    DeadLetterEntryRepositoryPort deadLetterRepository,
                                                    ApplicationEventPublisher eventPublisher) {
        PortfolioRepositoryPort portfolioRepository = mock(PortfolioRepositoryPort.class);
        StrategyRunnerRepositoryPort strategyRunnerRepository = mock(StrategyRunnerRepositoryPort.class);
        ExchangeAdapterRepositoryPort exchangeAdapterRepository = mock(ExchangeAdapterRepositoryPort.class);
        PortfolioBootSanityUseCase portfolioBootSanityUseCase = mock(PortfolioBootSanityUseCase.class);
        PortfolioReservationTtlUseCase portfolioReservationTtlUseCase = mock(PortfolioReservationTtlUseCase.class);
        PortfolioZombieDetectionUseCase portfolioZombieDetectionUseCase = mock(PortfolioZombieDetectionUseCase.class);
        RunnerBootRecoveryUseCase runnerBootRecoveryUseCase = mock(RunnerBootRecoveryUseCase.class);
        BootMetricsRecorder bootMetricsRecorder = mock(BootMetricsRecorder.class);

        when(portfolioRepository.findAll()).thenReturn(List.of(portfolio));
        when(strategyRunnerRepository.findByPortfolioId(portfolio.getId())).thenReturn(List.of(runner));
        when(portfolioZombieDetectionUseCase.execute(portfolio.getId(), "MOCK", true)).thenReturn(zombieResult);

        RunnerBootPhase1Properties phase1Properties = new RunnerBootPhase1Properties();
        phase1Properties.setEnabled(false);

        RunnerBootPhase2Properties phase2Properties = new RunnerBootPhase2Properties();
        phase2Properties.setEnabled(true);
        phase2Properties.setMode(mode);

        RunnerBootPhase3Properties phase3Properties = new RunnerBootPhase3Properties();
        phase3Properties.setEnabled(false);

        PortfolioSanityCheckProperties sanityProperties = new PortfolioSanityCheckProperties();
        sanityProperties.setEnabled(false);

        PortfolioReservationTtlProperties ttlProperties = new PortfolioReservationTtlProperties();
        ttlProperties.setEnabled(false);

        PortfolioZombieDetectionProperties zombieProperties = new PortfolioZombieDetectionProperties();
        zombieProperties.setEnabled(true);

        PortfolioCutoffProperties cutoffProperties = new PortfolioCutoffProperties();
        cutoffProperties.setEnabled(true);

        return new BootOrchestrator(
                portfolioRepository,
                strategyRunnerRepository,
                exchangeAdapterRepository,
                portfolioBootSanityUseCase,
                portfolioReservationTtlUseCase,
                portfolioZombieDetectionUseCase,
                deadLetterRepository,
                phase1Properties,
                phase2Properties,
                phase3Properties,
                sanityProperties,
                ttlProperties,
                zombieProperties,
                cutoffProperties,
                runnerBootRecoveryUseCase,
                tracker,
                bootMetricsRecorder,
                eventPublisher
        );
    }

    private static StrategyRunner activeRunner(UUID portfolioId, String exchangeId) {
        return new StrategyRunner(
                UUID.randomUUID(),
                portfolioId,
                "x1",
                UUID.randomUUID(),
                "SimpleMovingAverage",
                "BTCUSDT",
                exchangeId,
                Set.of(exchangeId),
                RunnerStatus.ACTIVE,
                ExecutionPolicy.SINGLE,
                AccountingPolicyType.FIFO,
                new BigDecimal("0.25"),
                1,
                1,
                null,
                false,
                Instant.now(),
                null,
                null,
                0L
        );
    }
}

