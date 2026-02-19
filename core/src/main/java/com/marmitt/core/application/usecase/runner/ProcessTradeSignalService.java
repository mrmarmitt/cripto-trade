package com.marmitt.core.application.usecase.runner;

import com.marmitt.core.application.exception.CapitalReservationRejectedException;
import com.marmitt.core.application.usecase.portfolio.ReserveCapitalService;
import com.marmitt.core.domain.runner.ClientOrderId;
import com.marmitt.core.domain.runner.Position;
import com.marmitt.core.domain.runner.StrategyRunner;
import com.marmitt.core.domain.runner.Transaction;
import com.marmitt.core.dto.capital.CapitalRequest;
import com.marmitt.core.dto.capital.ReservationResult;
import com.marmitt.core.dto.runner.OrderAck;
import com.marmitt.core.dto.runner.OrderDispatchCommand;
import com.marmitt.core.dto.strategy.StrategyOutputDto;
import com.marmitt.core.enums.ExecutionPolicy;
import com.marmitt.core.enums.TradingAction;
import com.marmitt.core.enums.TransactionType;
import com.marmitt.core.ports.outbound.exchange.OrderDispatchPort;
import com.marmitt.core.ports.outbound.repository.StrategyRunnerRepositoryPort;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

/**
 * Serviço central do fluxo ProcessTradeSignal (F2-01).
 * <p>
 * Implementa o protocolo Persist-First (IG Seção 6.2.1), em 9 passos:
 * <ol>
 *   <li>Validar Runner (canAcceptSignals)</li>
 *   <li>Validar sinal (HOLD → discard)</li>
 *   <li>Validar Execution Policy (SINGLE: rejeitar se posição aberta para BUY)</li>
 *   <li>Mapear TradingAction → TransactionType</li>
 *   <li>Gerar ClientOrderId</li>
 *   <li>Atomicamente: persistir Transaction PENDING + (SELL) lock de Position + reservar capital</li>
 *   <li>Despachar ordem para a exchange via {@code OrderDispatchPort}</li>
 *   <li>Processar ACK: ACCEPTED → submit(); REJECTED → reject(); TIMEOUT → manter PENDING</li>
 * </ol>
 * <p>
 * <b>Limite de @Transactional:</b> os passos 6a-6c (persist + lock + reserve) são
 * executados dentro de uma transação de banco gerenciada pelo Handler de Spring
 * {@code ProcessTradeSignalHandler.persistAndReserve()}, que delimita o limite @Transactional.
 * Se a reserva de capital falhar, {@link CapitalReservationRejectedException} é lançada
 * para acionar rollback automático. O dispatch (passo 7) ocorre FORA da transação.
 *
 * @implNote Pertence ao fluxo <b>ProcessTradeSignal</b>.
 *           Será integrado ao fluxo completo em refatoração futura (F2-01 absorção).
 * @see <a href="docs/IMPLEMENTATION_GUIDE.md">IG Seção 6.2.1</a>
 */
@Slf4j
public class ProcessTradeSignalService {

    /** Safety buffer aplicado ao amount antes de reservar capital (0.5%). */
    private static final BigDecimal SAFETY_BUFFER = new BigDecimal("1.005");

    private final StrategyRunnerRepositoryPort runnerRepository;
    private final ReserveCapitalService reserveCapitalService;
    private final OrderDispatchPort orderDispatchPort;

    public ProcessTradeSignalService(
            StrategyRunnerRepositoryPort runnerRepository,
            ReserveCapitalService reserveCapitalService,
            OrderDispatchPort orderDispatchPort
    ) {
        this.runnerRepository = runnerRepository;
        this.reserveCapitalService = reserveCapitalService;
        this.orderDispatchPort = orderDispatchPort;
    }

    // ============================================================
    // Passo 1-5: Validação e Preparação (fora da transação de banco)
    // ============================================================

    /**
     * Valida o sinal antes de entrar na zona transacional.
     * Retorna {@code false} se o sinal deve ser descartado silenciosamente.
     *
     * @param runner runner que recebeu o sinal
     * @param signal output da estratégia
     * @return {@code true} se o sinal deve ser processado; {@code false} para descartar
     */
    public boolean validate(StrategyRunner runner, StrategyOutputDto signal) {
        // Passo 1: Runner pode aceitar sinais
        if (!runner.canAcceptSignals()) {
            log.warn("processTradeSignal: runner={} cannot accept signals (status={} reconciling={})",
                    runner.getId(), runner.getStatus(), runner.isReconciling());
            return false;
        }

        // Passo 2: HOLD → discard
        if (signal.decision() == TradingAction.SHOULD_HOLD) {
            log.debug("processTradeSignal: HOLD signal discarded for runner={}", runner.getId());
            return false;
        }

        // Passo 3: Execution Policy SINGLE
        if (runner.getExecutionPolicy() == ExecutionPolicy.SINGLE) {
            TransactionType type = mapToTransactionType(signal.decision());
            if (type == TransactionType.BUY) {
                boolean hasOpenPositionOrInflight = hasOpenPositionOrInflight(runner);
                if (hasOpenPositionOrInflight) {
                    log.info("processTradeSignal: BUY signal rejected by SINGLE policy — " +
                                    "open position or in-flight orders exist for runner={}",
                            runner.getId());
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Prepara a Transaction para persistência — gera clientOrderId, calcula total.
     * Não persiste — a persistência é responsabilidade do Handler.
     *
     * @param runner       runner que processa o sinal
     * @param signal       output da estratégia
     * @param currentPrice preço de mercado corrente
     * @return Transaction no status PENDING, pronta para persistência
     */
    public Transaction buildTransaction(StrategyRunner runner, StrategyOutputDto signal,
                                        BigDecimal currentPrice) {
        TransactionType type = mapToTransactionType(signal.decision());
        String clientOrderId = ClientOrderId.generate(runner.getShortCode(), type);
        BigDecimal total = signal.quantity()
                .multiply(currentPrice)
                .setScale(8, RoundingMode.HALF_UP);

        return new Transaction(
                runner.getId(),
                clientOrderId,
                type,
                runner.getSymbol(),
                signal.quantity(),
                currentPrice,
                total,
                signal.confidence(),
                signal.reasoning(),
                signal.targetLotId()
        );
    }

    /**
     * Constrói o {@link CapitalRequest} para a reserva de capital.
     * Aplica o safety buffer de 0.5% ao amount (total * 1.005).
     *
     * @param runner      runner que solicita a reserva
     * @param transaction transaction criada em {@link #buildTransaction}
     * @return CapitalRequest pronto para {@code ReserveCapitalService.reserve()}
     */
    public CapitalRequest buildCapitalRequest(StrategyRunner runner, Transaction transaction) {
        BigDecimal amountWithBuffer = transaction.getTotal()
                .multiply(SAFETY_BUFFER)
                .setScale(8, RoundingMode.HALF_UP);

        return new CapitalRequest(
                transaction.getId(),
                runner.getId(),
                runner.getShortCode(),
                runner.getSymbol(),
                amountWithBuffer,
                transaction.getType()
        );
    }

    /**
     * Reserva capital. Lança {@link CapitalReservationRejectedException} se rejeitada,
     * para acionar rollback da transação de banco corrente.
     *
     * @param request capital request montado em {@link #buildCapitalRequest}
     * @throws CapitalReservationRejectedException se o capital não foi aprovado
     */
    public void reserveCapital(CapitalRequest request) {
        ReservationResult result = reserveCapitalService.reserve(request);
        if (result.isRejected()) {
            log.warn("processTradeSignal: capital reservation rejected — transactionId={} reason={}",
                    request.transactionId(), result.rejectionReason());
            throw new CapitalReservationRejectedException(request.transactionId(), result.rejectionReason());
        }
        log.debug("processTradeSignal: capital reserved — transactionId={} reservationId={}",
                request.transactionId(), result.reservationId());
    }

    // ============================================================
    // Passo 7-8: Dispatch e ACK (fora da transação de banco)
    // ============================================================

    /**
     * Despacha a ordem para a exchange e processa o ACK.
     * <p>
     * <ul>
     *   <li>ACCEPTED → chama {@code transaction.submit(exchangeOrderId)}</li>
     *   <li>REJECTED → chama {@code transaction.reject(reason)}</li>
     *   <li>TIMEOUT  → mantém PENDING (Boot Sequence reconcilia)</li>
     * </ul>
     * O caller deve persistir a Transaction após este método.
     *
     * @param runner      runner dono da transação
     * @param transaction transaction persistida com status PENDING
     * @return o ACK retornado pela exchange
     */
    public OrderAck dispatchAndApplyAck(StrategyRunner runner, Transaction transaction) {
        OrderDispatchCommand command = new OrderDispatchCommand(
                transaction.getClientOrderId(),
                runner.getId(),
                runner.getSymbol(),
                runner.getExchangeId(),
                transaction.getType(),
                transaction.getQuantity(),
                transaction.getPrice()
        );

        OrderAck ack = orderDispatchPort.dispatch(command);

        switch (ack.status()) {
            case ACCEPTED -> {
                transaction.submit(ack.exchangeOrderId());
                log.info("processTradeSignal: order accepted — clientOrderId={} exchangeOrderId={}",
                        transaction.getClientOrderId(), ack.exchangeOrderId());
            }
            case REJECTED -> {
                transaction.reject(ack.rejectionReason());
                log.warn("processTradeSignal: order rejected by exchange — clientOrderId={} reason={}",
                        transaction.getClientOrderId(), ack.rejectionReason());
            }
            case TIMEOUT -> log.warn(
                    "processTradeSignal: dispatch timeout — clientOrderId={} left as PENDING for reconciliation",
                    transaction.getClientOrderId());
        }

        return ack;
    }

    // ============================================================
    // Position helpers
    // ============================================================

    /**
     * Busca a Position alvo para uma operação de SELL.
     * Usa {@code targetLotId} se informado; caso contrário, busca a posição aberta do símbolo.
     *
     * @param runner runner dono da transação
     * @param signal output da estratégia
     * @return Position aberta, se encontrada
     */
    public Optional<Position> findTargetPosition(StrategyRunner runner, StrategyOutputDto signal) {
        if (signal.targetLotId() != null) {
            return runnerRepository.findPositionById(signal.targetLotId());
        }
        return runnerRepository.findOpenPositionByRunnerIdAndSymbol(runner.getId(), runner.getSymbol());
    }

    // ============================================================
    // Private helpers
    // ============================================================

    private TransactionType mapToTransactionType(TradingAction action) {
        return switch (action) {
            case SHOULD_BUY -> TransactionType.BUY;
            case SHOULD_SELL -> TransactionType.SELL;
            default -> throw new IllegalArgumentException("Cannot map TradingAction to TransactionType: " + action);
        };
    }

    private boolean hasOpenPositionOrInflight(StrategyRunner runner) {
        List<Position> openPositions = runnerRepository.findOpenPositionsByRunnerId(runner.getId());
        if (!openPositions.isEmpty()) return true;

        List<com.marmitt.core.domain.runner.Transaction> inflight =
                runnerRepository.findByRunnerIdAndStatuses(runner.getId(), List.of(
                        com.marmitt.core.enums.TransactionStatus.PENDING,
                        com.marmitt.core.enums.TransactionStatus.SUBMITTED,
                        com.marmitt.core.enums.TransactionStatus.PARTIAL
                ));
        return !inflight.isEmpty();
    }
}
