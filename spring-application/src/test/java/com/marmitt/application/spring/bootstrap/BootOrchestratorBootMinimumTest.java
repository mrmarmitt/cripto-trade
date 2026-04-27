package com.marmitt.application.spring.bootstrap;

import com.marmitt.core.application.usecase.boot.RunBootSequenceUseCase;
import com.marmitt.core.application.usecase.runner.RunnerBootRecoveryUseCase;
import com.marmitt.core.application.usecase.runner.orderconciliation.ConciliationOrderUpdateExecutor;
import com.marmitt.core.domain.Symbol;
import com.marmitt.core.dto.boot.BootRunSnapshot;
import com.marmitt.core.domain.portfolio.Portfolio;
import com.marmitt.core.domain.runner.StrategyRunner;
import com.marmitt.core.dto.portfolio.PortfolioZombieCandidate;
import com.marmitt.core.dto.portfolio.PortfolioZombieDetectionResult;
import com.marmitt.core.dto.websocket.data.OrderDataDto;
import com.marmitt.core.enums.AccountingPolicyType;
import com.marmitt.core.enums.BootFailureMode;
import com.marmitt.core.enums.BootRunStatus;
import com.marmitt.core.enums.DlqReason;
import com.marmitt.core.enums.ExecutionPolicy;
import com.marmitt.core.enums.RunnerStatus;
import com.marmitt.core.ports.outbound.exchange.rest.ExchangeOrderQueryPort;
import com.marmitt.core.ports.outbound.repository.DeadLetterEntryRepositoryPort;
import com.marmitt.core.ports.outbound.repository.ExchangeAdapterRepositoryPort;
import com.marmitt.core.ports.outbound.repository.GlobalBalanceRepositoryPort;
import com.marmitt.core.ports.outbound.repository.PortfolioRepositoryPort;
import com.marmitt.core.ports.outbound.repository.StrategyRunnerRepositoryPort;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BootOrchestratorBootMinimumTest {

    @Test
    void warnOnlyZombieDetectionDoesNotPersistDlqAndRunCompletes() {
        Portfolio portfolio = new Portfolio(UUID.randomUUID(), "p1");
        StrategyRunner runner = activeRunner(portfolio.getId(), "MOCK");
        PortfolioZombieDetectionResult detected = PortfolioZombieDetectionResult.detected(
                portfolio.getId(), "MOCK", 1, 1, 0, 0, 0, 0, List.of(
                        new PortfolioZombieCandidate(
                                "v1rx1t1234567890s001B_deadbeefcafe",
                                "EX_ORDER_123",
                                "BTCUSDT",
                                DlqReason.RECONCILIATION_CONFLICT,
                                "NO_LOCAL_MATCH"
                        )
                )
        );

        BootStatusTracker tracker = new BootStatusTracker();
        DeadLetterEntryRepositoryPort deadLetterRepository = mock(DeadLetterEntryRepositoryPort.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        BootOrchestrator orchestrator = newOrchestrator(
                BootFailureMode.WARN_ONLY,
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
    void failFastDetectedPersistsDlqSamplesAndPreservesPhaseSpecificFailureMetadata() {
        Portfolio portfolio = new Portfolio(UUID.randomUUID(), "p1");
        StrategyRunner runner = activeRunner(portfolio.getId(), "MOCK");
        PortfolioZombieDetectionResult detected = PortfolioZombieDetectionResult.detected(
                portfolio.getId(), "MOCK", 1, 1, 0, 0, 0, 0, List.of(
                        new PortfolioZombieCandidate(
                                "v1rx1t1234567890s001B_deadbeefcafe",
                                "EX_ORDER_123",
                                "BTCUSDT",
                                DlqReason.RECONCILIATION_CONFLICT,
                                "NO_LOCAL_MATCH"
                        )
                )
        );

        BootStatusTracker tracker = new BootStatusTracker();
        DeadLetterEntryRepositoryPort deadLetterRepository = mock(DeadLetterEntryRepositoryPort.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        BootOrchestrator orchestrator = newOrchestrator(
                BootFailureMode.FAIL_FAST,
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
        verify(deadLetterRepository, times(1)).save(any());
        verify(eventPublisher).publishEvent(any(BootFailFastEvent.class));
    }

    @Test
    void failFastFailedStatusPreservesPhaseSpecificFailureMetadata() {
        Portfolio portfolio = new Portfolio(UUID.randomUUID(), "p1");
        StrategyRunner runner = activeRunner(portfolio.getId(), "MOCK");
        PortfolioZombieDetectionResult failed = PortfolioZombieDetectionResult.failed(
                portfolio.getId(),
                "MOCK",
                "QUERY_FAILED",
                "Simulated exchange query failure"
        );

        BootStatusTracker tracker = new BootStatusTracker();
        DeadLetterEntryRepositoryPort deadLetterRepository = mock(DeadLetterEntryRepositoryPort.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        BootOrchestrator orchestrator = newOrchestrator(
                BootFailureMode.FAIL_FAST,
                portfolio,
                runner,
                failed,
                tracker,
                deadLetterRepository,
                eventPublisher
        );

        assertThrows(IllegalStateException.class, orchestrator::onApplicationReady);

        BootRunSnapshot snapshot = tracker.snapshot();
        assertEquals(BootRunStatus.FAILED, snapshot.status());
        assertEquals("phase2.zombie", snapshot.failurePhase());
        verifyNoInteractions(deadLetterRepository);
        verify(eventPublisher).publishEvent(any(BootFailFastEvent.class));
    }

    @Test
    void unexpectedPhaseRuntimePreservesSpecificFailurePhase() {
        Portfolio portfolio = new Portfolio(UUID.randomUUID(), "p1");
        StrategyRunner runner = activeRunner(portfolio.getId(), "MOCK");

        BootStatusTracker tracker = new BootStatusTracker();
        DeadLetterEntryRepositoryPort deadLetterRepository = mock(DeadLetterEntryRepositoryPort.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        BootOrchestrator orchestrator = newOrchestratorWithZombieFailure(
                portfolio,
                runner,
                tracker,
                deadLetterRepository,
                eventPublisher
        );

        assertThrows(IllegalStateException.class, orchestrator::onApplicationReady);

        BootRunSnapshot snapshot = tracker.snapshot();
        assertEquals(BootRunStatus.FAILED, snapshot.status());
        assertEquals("phase2.zombie", snapshot.failurePhase());
        verify(deadLetterRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    private static BootOrchestrator newOrchestrator(BootFailureMode mode,
                                                    Portfolio portfolio,
                                                    StrategyRunner runner,
                                                    PortfolioZombieDetectionResult zombieResult,
                                                    BootStatusTracker tracker,
                                                    DeadLetterEntryRepositoryPort deadLetterRepository,
                                                    ApplicationEventPublisher eventPublisher) {
        PortfolioRepositoryPort portfolioRepository = mock(PortfolioRepositoryPort.class);
        StrategyRunnerRepositoryPort strategyRunnerRepository = mock(StrategyRunnerRepositoryPort.class);
        ExchangeAdapterRepositoryPort exchangeAdapterRepository = mock(ExchangeAdapterRepositoryPort.class);
        GlobalBalanceRepositoryPort globalBalanceRepository = mock(GlobalBalanceRepositoryPort.class);
        RunnerBootRecoveryUseCase runnerBootRecoveryUseCase = mock(RunnerBootRecoveryUseCase.class);
        ConciliationOrderUpdateExecutor conciliationOrderUpdate = mock(ConciliationOrderUpdateExecutor.class);
        BootMetricsRecorder bootMetricsRecorder = mock(BootMetricsRecorder.class);

        when(portfolioRepository.findAll()).thenReturn(List.of(portfolio));
        when(strategyRunnerRepository.findByPortfolioId(portfolio.getId())).thenReturn(List.of(runner));
        stubZombieDetection(exchangeAdapterRepository, strategyRunnerRepository, runner, zombieResult);

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

        RunBootSequenceUseCase runBootSequenceUseCase = new RunBootSequenceUseCase(
                portfolioRepository,
                strategyRunnerRepository,
                exchangeAdapterRepository,
                globalBalanceRepository,
                deadLetterRepository,
                conciliationOrderUpdate,
                runnerBootRecoveryUseCase
        );

        return new BootOrchestrator(
                phase1Properties,
                phase2Properties,
                phase3Properties,
                sanityProperties,
                ttlProperties,
                zombieProperties,
                cutoffProperties,
                runBootSequenceUseCase,
                tracker,
                bootMetricsRecorder,
                eventPublisher
        );
    }

    private static BootOrchestrator newOrchestratorWithZombieFailure(Portfolio portfolio,
                                                                     StrategyRunner runner,
                                                                     BootStatusTracker tracker,
                                                                     DeadLetterEntryRepositoryPort deadLetterRepository,
                                                                     ApplicationEventPublisher eventPublisher) {
        PortfolioRepositoryPort portfolioRepository = mock(PortfolioRepositoryPort.class);
        StrategyRunnerRepositoryPort strategyRunnerRepository = mock(StrategyRunnerRepositoryPort.class);
        ExchangeAdapterRepositoryPort exchangeAdapterRepository = mock(ExchangeAdapterRepositoryPort.class);
        GlobalBalanceRepositoryPort globalBalanceRepository = mock(GlobalBalanceRepositoryPort.class);
        RunnerBootRecoveryUseCase runnerBootRecoveryUseCase = mock(RunnerBootRecoveryUseCase.class);
        ConciliationOrderUpdateExecutor conciliationOrderUpdate = mock(ConciliationOrderUpdateExecutor.class);
        BootMetricsRecorder bootMetricsRecorder = mock(BootMetricsRecorder.class);

        when(portfolioRepository.findAll()).thenReturn(List.of(portfolio));
        when(strategyRunnerRepository.findByPortfolioId(portfolio.getId())).thenReturn(List.of(runner));
        PortfolioZombieDetectionResult detected = PortfolioZombieDetectionResult.detected(
                portfolio.getId(), "MOCK", 1, 1, 0, 0, 0, 0, List.of(
                        new PortfolioZombieCandidate(
                                "v1rx1t1234567890s001B_deadbeefcafe",
                                "EX_ORDER_123",
                                "BTCUSDT",
                                DlqReason.RECONCILIATION_CONFLICT,
                                "NO_LOCAL_MATCH"
                        )
                )
        );
        stubZombieDetection(exchangeAdapterRepository, strategyRunnerRepository, runner, detected);
        when(deadLetterRepository.existsUnresolvedByIdentity(any(), any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("Simulated unexpected zombie failure"));

        RunnerBootPhase1Properties phase1Properties = new RunnerBootPhase1Properties();
        phase1Properties.setEnabled(false);

        RunnerBootPhase2Properties phase2Properties = new RunnerBootPhase2Properties();
        phase2Properties.setEnabled(true);
        phase2Properties.setMode(BootFailureMode.FAIL_FAST);

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

        RunBootSequenceUseCase runBootSequenceUseCase = new RunBootSequenceUseCase(
                portfolioRepository,
                strategyRunnerRepository,
                exchangeAdapterRepository,
                globalBalanceRepository,
                deadLetterRepository,
                conciliationOrderUpdate,
                runnerBootRecoveryUseCase
        );

        return new BootOrchestrator(
                phase1Properties,
                phase2Properties,
                phase3Properties,
                sanityProperties,
                ttlProperties,
                zombieProperties,
                cutoffProperties,
                runBootSequenceUseCase,
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

    private static void stubZombieDetection(ExchangeAdapterRepositoryPort exchangeAdapterRepository,
                                            StrategyRunnerRepositoryPort strategyRunnerRepository,
                                            StrategyRunner runner,
                                            PortfolioZombieDetectionResult result) {
        ExchangeOrderQueryPort orderQuery = mock(ExchangeOrderQueryPort.class);
        when(exchangeAdapterRepository.findOrderQueryByName("MOCK")).thenReturn(Optional.of(orderQuery));

        if (result.status().name().equals("FAILED")) {
            when(orderQuery.listAllOpenOrders()).thenThrow(new IllegalStateException(result.message()));
            return;
        }

        if (result.samples().isEmpty()) {
            when(orderQuery.listAllOpenOrders()).thenReturn(List.of());
            return;
        }

        List<OrderDataDto> openOrders = result.samples().stream()
                .map(sample -> toOpenOrder(sample, runner))
                .toList();
        when(orderQuery.listAllOpenOrders()).thenReturn(openOrders);
        result.samples().stream()
                .map(PortfolioZombieCandidate::clientOrderId)
                .filter(clientOrderId -> clientOrderId != null && !clientOrderId.isBlank())
                .forEach(clientOrderId ->
                        when(strategyRunnerRepository.findTransactionByClientOrderId(clientOrderId))
                                .thenReturn(Optional.empty()));
    }

    private static OrderDataDto toOpenOrder(PortfolioZombieCandidate sample, StrategyRunner runner) {
        return new OrderDataDto(
                sample.exchangeOrderId(),
                sample.clientOrderId(),
                Symbol.of(sample.symbol() != null ? sample.symbol() : runner.getSymbol()),
                OrderDataDto.OrderSide.BUY,
                OrderDataDto.OrderType.LIMIT,
                new BigDecimal("0.001"),
                BigDecimal.ZERO,
                new BigDecimal("65000"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                OrderDataDto.OrderStatus.NEW,
                null,
                Instant.now()
        );
    }
}
