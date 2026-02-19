package com.marmitt.core.application.usecase.portfolio;

import com.marmitt.core.domain.portfolio.GlobalBalance;
import com.marmitt.core.domain.portfolio.Portfolio;
import com.marmitt.core.domain.runner.StrategyRunner;
import com.marmitt.core.domain.runner.Transaction;
import com.marmitt.core.dto.capital.CapitalRequest;
import com.marmitt.core.dto.capital.ReservationResult;
import com.marmitt.core.enums.RejectionReason;
import com.marmitt.core.enums.TransactionStatus;
import com.marmitt.core.ports.outbound.repository.GlobalBalanceRepositoryPort;
import com.marmitt.core.ports.outbound.repository.PortfolioRepositoryPort;
import com.marmitt.core.ports.outbound.repository.StrategyRunnerRepositoryPort;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Serviço de reserva de capital síncrona (F1-10).
 * <p>
 * Ponto de serialização do {@code GlobalBalance}: move capital de Available → Reserved
 * com lock pessimista no banco, garantindo que dois Runners não façam double-spend.
 * <p>
 * Sequência de validações (IG Seção 5.2.1):
 * <ol>
 *   <li>Runner existe e está operacional → {@code UNKNOWN_RUNNER}</li>
 *   <li>Portfolio existe e SafeMode está NORMAL → {@code RISK_VIOLATION}</li>
 *   <li>Limite de alocação do Runner não excedido → {@code RUNNER_LIMIT_EXCEEDED}</li>
 *   <li>Reserva atômica com lock pessimista → {@code INSUFFICIENT_FUNDS}</li>
 * </ol>
 *
 * @implNote Pertence ao fluxo <b>ProcessTradeSignal</b> — executado entre {@code persist(PENDING)}
 *           e o Order Dispatch. Será absorvido por esse fluxo em refatoração futura.
 * @see <a href="docs/IMPLEMENTATION_GUIDE.md">IG Seções 5.2.1, 7.1</a>
 */
@Slf4j
public class ReserveCapitalService {

    private static final List<TransactionStatus> INFLIGHT_STATUSES = List.of(
            TransactionStatus.PENDING,
            TransactionStatus.SUBMITTED,
            TransactionStatus.PARTIAL
    );

    private final StrategyRunnerRepositoryPort runnerRepository;
    private final PortfolioRepositoryPort portfolioRepository;
    private final GlobalBalanceRepositoryPort globalBalanceRepository;

    public ReserveCapitalService(
            StrategyRunnerRepositoryPort runnerRepository,
            PortfolioRepositoryPort portfolioRepository,
            GlobalBalanceRepositoryPort globalBalanceRepository
    ) {
        this.runnerRepository = runnerRepository;
        this.portfolioRepository = portfolioRepository;
        this.globalBalanceRepository = globalBalanceRepository;
    }

    /**
     * Reserva capital para uma nova ordem.
     * <p>
     * <b>Síncrono e bloqueante.</b> Ponto de serialização do GlobalBalance.
     * Deve ser chamado após a Transaction ser persistida como PENDING e antes do
     * Order Dispatch. Se retornar REJECTED, a Transaction deve ser marcada como
     * REJECTED e o sinal descartado.
     *
     * @param request payload com transactionId, runnerId, symbol, amount e tipo
     * @return {@link ReservationResult} APPROVED (com reservationId) ou REJECTED (com motivo)
     */
    public ReservationResult reserve(CapitalRequest request) {
        log.debug("reserve: transactionId={} runnerId={} amount={}",
                request.transactionId(), request.runnerId(), request.amount());

        // ── Passo 1: verificar Runner ──────────────────────────────────────────
        StrategyRunner runner = runnerRepository.findById(request.runnerId()).orElse(null);
        if (runner == null) {
            log.warn("reserve rejected [UNKNOWN_RUNNER]: runner {} not found", request.runnerId());
            return ReservationResult.rejected(RejectionReason.UNKNOWN_RUNNER);
        }
        if (!runner.getStatus().isOperational()) {
            log.warn("reserve rejected [UNKNOWN_RUNNER]: runner {} not operational (status={})",
                    request.runnerId(), runner.getStatus());
            return ReservationResult.rejected(RejectionReason.UNKNOWN_RUNNER);
        }

        // ── Passo 2: verificar Portfolio e Safe Mode ───────────────────────────
        Portfolio portfolio = portfolioRepository.findById(runner.getPortfolioId()).orElse(null);
        if (portfolio == null) {
            log.error("reserve rejected [UNKNOWN_RUNNER]: portfolio {} not found for runner {}",
                    runner.getPortfolioId(), request.runnerId());
            return ReservationResult.rejected(RejectionReason.UNKNOWN_RUNNER);
        }
        if (portfolio.getSafeModeStatus().isActive()) {
            log.warn("reserve rejected [RISK_VIOLATION]: safe mode {} active for portfolio {}",
                    portfolio.getSafeModeStatus(), runner.getPortfolioId());
            return ReservationResult.rejected(RejectionReason.RISK_VIOLATION);
        }

        // ── Passo 3: verificar limite de alocação do Runner ───────────────────
        GlobalBalance balance = globalBalanceRepository
                .findByPortfolioId(runner.getPortfolioId())
                .orElse(null);
        if (balance == null) {
            log.error("reserve rejected [INSUFFICIENT_FUNDS]: global balance not found for portfolio {}",
                    runner.getPortfolioId());
            return ReservationResult.rejected(RejectionReason.INSUFFICIENT_FUNDS);
        }

        BigDecimal currentExposure = calculateRunnerExposure(request.runnerId());
        BigDecimal totalBalance = balance.getTotalBalance();
        BigDecimal maxAllocation = runner.getMaxAllocationPercent().multiply(totalBalance);

        if (currentExposure.add(request.amount()).compareTo(maxAllocation) > 0) {
            log.warn("reserve rejected [RUNNER_LIMIT_EXCEEDED]: runner={} currentExposure={} requested={} maxAllocation={}",
                    request.runnerId(), currentExposure, request.amount(), maxAllocation);
            return ReservationResult.rejected(RejectionReason.RUNNER_LIMIT_EXCEEDED);
        }

        // ── Passo 4: reserva atômica com lock pessimista ──────────────────────
        boolean reserved = globalBalanceRepository.reserveAtomic(runner.getPortfolioId(), request.amount());
        if (!reserved) {
            log.warn("reserve rejected [INSUFFICIENT_FUNDS]: atomic reserve failed for portfolio={} amount={}",
                    runner.getPortfolioId(), request.amount());
            return ReservationResult.rejected(RejectionReason.INSUFFICIENT_FUNDS);
        }

        UUID reservationId = UUID.randomUUID();
        log.info("reserve approved: transactionId={} runnerId={} amount={} portfolioId={} reservationId={}",
                request.transactionId(), request.runnerId(), request.amount(),
                runner.getPortfolioId(), reservationId);
        return ReservationResult.approved(reservationId);
    }

    /**
     * Calcula a exposição corrente do Runner:
     * soma dos {@code total} de todas as Transactions em voo (PENDING, SUBMITTED, PARTIAL).
     */
    private BigDecimal calculateRunnerExposure(UUID runnerId) {
        List<Transaction> inflight = runnerRepository.findByRunnerIdAndStatuses(runnerId, INFLIGHT_STATUSES);
        return inflight.stream()
                .map(Transaction::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
