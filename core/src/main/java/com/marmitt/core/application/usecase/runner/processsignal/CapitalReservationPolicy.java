package com.marmitt.core.application.usecase.runner.processsignal;

import com.marmitt.core.application.exception.CapitalReservationRejectedException;
import com.marmitt.core.domain.portfolio.GlobalBalance;
import com.marmitt.core.domain.portfolio.Portfolio;
import com.marmitt.core.domain.runner.StrategyRunner;
import com.marmitt.core.dto.capital.CapitalRequest;
import com.marmitt.core.enums.RejectionReason;
import com.marmitt.core.ports.outbound.repository.GlobalBalanceRepositoryPort;
import com.marmitt.core.ports.outbound.repository.PortfolioRepositoryPort;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;

/**
 * Politica de reserva de capital para ordens BUY.
 *
 * Centraliza as regras de aprovacao de reserva:
 * - Portfolio existente e fora de Safe Mode.
 * - Limite de exposicao do runner.
 * - Reserva atomica de saldo no GlobalBalance.
 */
@Slf4j
class CapitalReservationPolicy {

    private final PortfolioRepositoryPort portfolioRepository;
    private final GlobalBalanceRepositoryPort globalBalanceRepository;
    private final RunnerExposureService exposureService;

    public CapitalReservationPolicy(PortfolioRepositoryPort portfolioRepository,
                                    GlobalBalanceRepositoryPort globalBalanceRepository,
                                    RunnerExposureService exposureService) {
        this.portfolioRepository = portfolioRepository;
        this.globalBalanceRepository = globalBalanceRepository;
        this.exposureService = exposureService;
    }

    /**
     * Valida pre-condicoes de risco/capital e aplica a reserva atomica.
     *
     * @throws CapitalReservationRejectedException quando qualquer regra de aprovacao falha
     */
    public void validateAndReserve(CapitalRequest capitalRequest, StrategyRunner runner) {
        validateAndReserve(capitalRequest, runner, null);
    }

    /**
     * Mesmo fluxo de validacao/reserva, permitindo receber exposicao pre-calculada
     * para evitar consulta duplicada no caminho de BUY.
     */
    public void validateAndReserve(CapitalRequest capitalRequest,
                                   StrategyRunner runner,
                                   BigDecimal precomputedExposure) {
        Portfolio portfolio = portfolioRepository.findById(runner.getPortfolioId()).orElse(null);
        if (portfolio == null) {
            log.error("persistBuyAndReserve: portfolio {} not found for runner {}",
                    runner.getPortfolioId(), runner.getId());
            throw new CapitalReservationRejectedException(capitalRequest.transactionId(), RejectionReason.UNKNOWN_RUNNER);
        }
        if (portfolio.getSafeModeStatus().isActive()) {
            log.warn("persistBuyAndReserve: safe mode {} active for portfolio {}",
                    portfolio.getSafeModeStatus(), runner.getPortfolioId());
            throw new CapitalReservationRejectedException(capitalRequest.transactionId(), RejectionReason.RISK_VIOLATION);
        }

        GlobalBalance balance = globalBalanceRepository.findByPortfolioId(runner.getPortfolioId()).orElse(null);
        if (balance == null) {
            log.error("persistBuyAndReserve: global balance not found for portfolio {}",
                    runner.getPortfolioId());
            throw new CapitalReservationRejectedException(capitalRequest.transactionId(), RejectionReason.INSUFFICIENT_FUNDS);
        }

        BigDecimal currentExposure = precomputedExposure != null
                ? precomputedExposure
                : exposureService.calculateInFlightExposure(runner.getId());
        BigDecimal maxAllocation = runner.getMaxAllocationPercent().multiply(balance.getTotalBalance());
        if (currentExposure.add(capitalRequest.amount()).compareTo(maxAllocation) > 0) {
            log.warn("persistBuyAndReserve: RUNNER_LIMIT_EXCEEDED runner={} currentExposure={} requested={} maxAllocation={}",
                    runner.getId(), currentExposure, capitalRequest.amount(), maxAllocation);
            throw new CapitalReservationRejectedException(capitalRequest.transactionId(), RejectionReason.RUNNER_LIMIT_EXCEEDED);
        }

        boolean reserved = globalBalanceRepository.reserveAtomic(runner.getPortfolioId(), capitalRequest.amount());
        if (!reserved) {
            log.warn("persistBuyAndReserve: INSUFFICIENT_FUNDS atomic reserve failed for portfolio={} amount={}",
                    runner.getPortfolioId(), capitalRequest.amount());
            throw new CapitalReservationRejectedException(capitalRequest.transactionId(), RejectionReason.INSUFFICIENT_FUNDS);
        }
    }
}


