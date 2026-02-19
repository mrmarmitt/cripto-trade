package com.marmitt.core.application.usecase.runner;

import com.marmitt.core.application.exception.CapitalReservationRejectedException;
import com.marmitt.core.domain.runner.Position;
import com.marmitt.core.domain.runner.StrategyRunner;
import com.marmitt.core.domain.runner.Transaction;
import com.marmitt.core.dto.capital.CapitalRequest;
import com.marmitt.core.dto.runner.OrderAck;
import com.marmitt.core.dto.strategy.StrategyOutputDto;
import com.marmitt.core.enums.TransactionType;
import com.marmitt.core.ports.inbound.runner.HandleOrderTerminationPort;
import com.marmitt.core.ports.inbound.runner.ProcessTradeSignalTransactionPort;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * UseCase central do fluxo ProcessTradeSignal (F2-01).
 * <p>
 * Implementa o protocolo Persist-First (IG Seção 6.2.1) em sua totalidade:
 * <ol>
 *   <li>Validar Runner e sinal (delega ao {@link ProcessTradeSignalService})</li>
 *   <li>Construir Transaction e CapitalRequest</li>
 *   <li>Localizar Position alvo (SELL)</li>
 *   <li>Persistir PENDING + lock Position + reservar capital — via {@link ProcessTradeSignalTransactionPort}</li>
 *   <li>Despachar ordem para a exchange</li>
 *   <li>Rotear ACK: ACCEPTED → persistir SUBMITTED; REJECTED → encerrar; TIMEOUT → manter PENDING</li>
 * </ol>
 * <p>
 * <b>Limites @Transactional:</b> delegados ao {@link ProcessTradeSignalTransactionPort},
 * implementado no módulo {@code spring-application}. Este UseCase não possui dependências Spring.
 *
 * @see ProcessTradeSignalService
 * @see ProcessTradeSignalTransactionPort
 * @see <a href="docs/IMPLEMENTATION_GUIDE.md">IG Seção 6.2.1</a>
 */
@Slf4j
public class ProcessTradeSignalUseCase {

    private final ProcessTradeSignalService service;
    private final ProcessTradeSignalTransactionPort transactionPort;
    private final HandleOrderTerminationPort terminationPort;

    public ProcessTradeSignalUseCase(
            ProcessTradeSignalService service,
            ProcessTradeSignalTransactionPort transactionPort,
            HandleOrderTerminationPort terminationPort
    ) {
        this.service = service;
        this.transactionPort = transactionPort;
        this.terminationPort = terminationPort;
    }

    /**
     * Executa o protocolo Persist-First completo para o sinal recebido.
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

        // Passo 6: localiza Position alvo (SELL)
        Optional<Position> targetPosition = Optional.empty();
        if (transaction.getType() == TransactionType.SELL) {
            targetPosition = service.findTargetPosition(runner, signal);
            if (targetPosition.isEmpty()) {
                log.warn("processTradeSignal: SELL signal discarded — no open position for runner={} symbol={}",
                        runner.getId(), runner.getSymbol());
                return;
            }
        }

        // Passo 6 (cont.): persiste PENDING + lock Position + reserva capital — atômico
        try {
            transactionPort.persistAndReserve(transaction, targetPosition.orElse(null), capitalRequest);
        } catch (CapitalReservationRejectedException ex) {
            log.warn("processTradeSignal: signal discarded — capital rejected transactionId={} reason={}",
                    ex.getTransactionId(), ex.getReason());
            return;
        }

        // Passo 7: dispatch para a exchange
        OrderAck ack = service.dispatchAndApplyAck(runner, transaction);

        // Passo 8: roteamento do ACK
        if (ack.isTimeout()) {
            // Transaction permanece PENDING — Boot Sequence (F2-06) reconcilia
            return;
        }
        if (ack.isRejected()) {
            // Unlock de Position (SELL) + persistência + release de margem
            terminationPort.handle(transaction);
            return;
        }
        // ACCEPTED: persiste Transaction como SUBMITTED
        transactionPort.persistSubmitted(transaction);
    }
}
