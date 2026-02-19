package com.marmitt.application.spring.handler;

import com.marmitt.core.application.exception.CapitalReservationRejectedException;
import com.marmitt.core.application.usecase.runner.ProcessTradeSignalService;
import com.marmitt.core.domain.runner.Position;
import com.marmitt.core.domain.runner.StrategyRunner;
import com.marmitt.core.domain.runner.Transaction;
import com.marmitt.core.dto.capital.CapitalRequest;
import com.marmitt.core.dto.runner.OrderAck;
import com.marmitt.core.dto.strategy.StrategyOutputDto;
import com.marmitt.core.enums.TransactionType;
import com.marmitt.core.ports.outbound.repository.StrategyRunnerRepositoryPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Handler Spring do fluxo ProcessTradeSignal (F2-01).
 * <p>
 * Coordena o protocolo Persist-First delegando lógica de domínio ao
 * {@link ProcessTradeSignalService} e gerenciando os limites @Transactional.
 * <p>
 * <b>Limites de transação:</b>
 * <ul>
 *   <li>{@link #handle}: ponto de entrada — sem transação ativa. Válida e prepara o sinal.
 *       Chama {@link #persistAndReserve} dentro de uma transação, depois dispatch fora.</li>
 *   <li>{@link #persistAndReserve}: {@code @Transactional(rollbackFor = ...)} — persiste
 *       Transaction PENDING, aplica lock de Position (SELL) e reserva capital atomicamente.
 *       Lança {@link CapitalReservationRejectedException} para acionar rollback.</li>
 *   <li>{@link #handleAck}: {@code @Transactional} — atualiza status da Transaction
 *       após receber ACK da exchange.</li>
 * </ul>
 *
 * @see <a href="docs/IMPLEMENTATION_GUIDE.md">IG Seção 6.2.1</a>
 */
@Component
@Slf4j
public class ProcessTradeSignalHandler {

    private final ProcessTradeSignalService service;
    private final StrategyRunnerRepositoryPort runnerRepository;

    public ProcessTradeSignalHandler(
            ProcessTradeSignalService service,
            StrategyRunnerRepositoryPort runnerRepository
    ) {
        this.service = service;
        this.runnerRepository = runnerRepository;
    }

    /**
     * Ponto de entrada do fluxo ProcessTradeSignal.
     * <p>
     * Sem transação ativa — orquestra os passos 1-8 do protocolo Persist-First.
     *
     * @param runner       runner que recebeu o sinal
     * @param signal       output da estratégia
     * @param currentPrice preço de mercado corrente
     */
    public void handle(StrategyRunner runner, StrategyOutputDto signal, BigDecimal currentPrice) {
        // Passos 1-3: validação
        if (!service.validate(runner, signal)) {
            return;
        }

        // Passos 4-5: construção da Transaction (sem persistência)
        Transaction transaction = service.buildTransaction(runner, signal, currentPrice);
        CapitalRequest capitalRequest = service.buildCapitalRequest(runner, transaction);

        // Passo 6: persiste Transaction + lock Position (SELL) + reserva capital — atômico
        Optional<Position> targetPosition = Optional.empty();
        if (transaction.getType() == TransactionType.SELL) {
            targetPosition = service.findTargetPosition(runner, signal);
            if (targetPosition.isEmpty()) {
                log.warn("processTradeSignal: SELL signal discarded — no open position for runner={} symbol={}",
                        runner.getId(), runner.getSymbol());
                return;
            }
        }

        try {
            persistAndReserve(transaction, targetPosition.orElse(null), capitalRequest);
        } catch (CapitalReservationRejectedException ex) {
            log.warn("processTradeSignal: signal discarded — capital rejected transactionId={} reason={}",
                    ex.getTransactionId(), ex.getReason());
            return;
        }

        // Passo 7-8: dispatch e ACK (fora da transação de banco)
        OrderAck ack = service.dispatchAndApplyAck(runner, transaction);
        handleAck(transaction, ack);
    }

    /**
     * Persiste atomicamente Transaction PENDING, lock de Position (quando SELL) e reserva capital.
     * <p>
     * {@code @Transactional} garante rollback automático se {@link CapitalReservationRejectedException}
     * for lançada — desfazendo a persistência da Transaction e do lock de Position.
     *
     * @param transaction    transaction criada em {@link ProcessTradeSignalService#buildTransaction}
     * @param targetPosition position a bloquear (SELL); null para BUY
     * @param capitalRequest request de reserva criado em {@link ProcessTradeSignalService#buildCapitalRequest}
     * @throws CapitalReservationRejectedException se a reserva de capital falhar
     */
    @Transactional(rollbackFor = CapitalReservationRejectedException.class)
    public void persistAndReserve(Transaction transaction, Position targetPosition,
                                  CapitalRequest capitalRequest) {
        if (targetPosition != null) {
            // Passo 6a-6b: persiste Transaction e aplica lock de Position atomicamente
            targetPosition.lock(transaction.getId(), transaction.getQuantity());
            targetPosition.startClosing();
            runnerRepository.saveAtomicTransactionAndPositionLock(transaction, targetPosition);
        } else {
            // Passo 6a (BUY): persiste Transaction apenas
            runnerRepository.saveTransaction(transaction);
        }

        // Passo 6c: reserva capital — lança exceção para rollback se rejeitada
        service.reserveCapital(capitalRequest);
    }

    /**
     * Persiste o status da Transaction após o ACK da exchange.
     * <p>
     * {@code @Transactional} — ACCEPTED e REJECTED resultam em salvar status atualizado.
     * TIMEOUT não salva (Transaction permanece PENDING para reconciliação no Boot Sequence).
     *
     * @param transaction transaction com status já atualizado pelo {@code dispatchAndApplyAck}
     * @param ack         ACK retornado pela exchange
     */
    @Transactional
    public void handleAck(Transaction transaction, OrderAck ack) {
        if (ack.isTimeout()) {
            // Transaction permanece PENDING — Boot Sequence (F2-06) reconcilia
            return;
        }
        runnerRepository.saveTransaction(transaction);
    }
}
