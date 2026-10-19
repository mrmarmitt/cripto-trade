package com.marmitt.core.application.usecase.runner.processsignal;

import com.marmitt.core.domain.runner.Position;
import com.marmitt.core.domain.runner.StrategyRunner;
import com.marmitt.core.domain.runner.Transaction;
import com.marmitt.core.enums.TransactionStatus;
import com.marmitt.core.ports.outbound.repository.StrategyRunnerRepositoryPort;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Calcula a exposicao financeira atual do runner: capital comprometido em posicoes abertas
 * e ordens em voo que ainda nao foram liquidadas.
 *
 * <p><b>Exposicao em voo</b> e o somatorio dos valores totais de transacoes nos status
 * PENDING, SUBMITTED ou PARTIAL — capital que ja foi reservado mas cuja execucao final
 * (FILLED) ou falha (CANCELED/REJECTED/EXPIRED) ainda nao foi confirmada pela exchange.
 *
 * <p>Essa informacao e usada em dois contextos:
 * <ul>
 *   <li>{@link RunnerSignalPolicy}: para verificar se o runner SINGLE ja tem posicao
 *       aberta ou ordem em voo antes de aceitar um novo BUY.</li>
 *   <li>{@link PreTradeRiskPolicy}: limites {@code maxOpenPositions}/{@code maxPendingOrders} (T50).</li>
 *   <li>{@link CapitalReservationPolicy}: para calcular se o novo BUY ultrapassaria
 *       o limite de alocacao maxima ({@code maxAllocationPercent}) do runner.</li>
 * </ul>
 *
 * <p>{@link #loadSnapshot} e o metodo preferido no caminho BUY com politica SINGLE,
 * pois carrega posicoes e transacoes em uma unica chamada e compartilha o resultado
 * entre a policy e a reserva — evitando consultas duplicadas ao banco.
 */
public class RunnerExposureService {

    private final StrategyRunnerRepositoryPort strategyRunnerRepository;

    public RunnerExposureService(StrategyRunnerRepositoryPort strategyRunnerRepository) {
        this.strategyRunnerRepository = strategyRunnerRepository;
    }

    /**
     * Calcula o valor financeiro total em voo para o runner.
     * Usado como fallback quando o snapshot nao foi pre-carregado
     * (ex.: reserva sem snapshot pre-carregado).
     */
    public BigDecimal calculateInFlightExposure(UUID runnerId) {
        return inFlightSummary(runnerId).exposure();
    }

    /**
     * Exposicao em voo e a ordem em voo mais antiga do runner. Fonte unica da exposicao usada pela
     * reserva e pela metrica de utilizacao de capital (T51).
     */
    public InFlightSummary inFlightSummary(UUID runnerId) {
        List<Transaction> inFlight = strategyRunnerRepository.findByRunnerIdAndStatuses(runnerId, TransactionStatus.IN_FLIGHT);
        return new InFlightSummary(sumTotals(inFlight), inFlight.stream()
                .map(Transaction::getRequestedAt)
                .min(Instant::compareTo)
                .orElse(null));
    }

    /**
     * Carrega posicoes abertas e ordens em voo em uma unica consulta e retorna
     * um snapshot imutavel com as informacoes necessarias para a policy e a reserva.
     *
     * <p>Preferivel a {@link #calculateInFlightExposure} quando o caller precisa tanto
     * do flag booleano (para {@link RunnerSignalPolicy}) quanto do valor numerico (para
     * {@link CapitalReservationPolicy}), evitando duas roundtrips ao banco.
     */
    public ExposureSnapshot loadSnapshot(StrategyRunner runner) {
        List<Position> openPositions = strategyRunnerRepository.findOpenPositionsByRunnerId(runner.getId());
        List<Transaction> inFlight = strategyRunnerRepository.findByRunnerIdAndStatuses(runner.getId(), TransactionStatus.IN_FLIGHT);

        return new ExposureSnapshot(openPositions.size(), inFlight.size(), sumTotals(inFlight));
    }

    private static BigDecimal sumTotals(List<Transaction> inFlight) {
        return inFlight.stream()
                .map(Transaction::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * @param exposure        somatorio dos totais das ordens em voo
     * @param oldestRequestedAt envio da ordem em voo mais antiga, {@code null} sem ordem
     */
    public record InFlightSummary(BigDecimal exposure, Instant oldestRequestedAt) {
    }

    /**
     * Foto do estado de exposicao do runner no momento do tick.
     *
     * @param openPositions    posicoes abertas — limite {@code maxOpenPositions} (T50)
     * @param inFlightOrders   ordens em voo (PENDING, SUBMITTED, PARTIAL) — limite {@code maxPendingOrders} (T50)
     * @param inFlightExposure somatorio dos valores totais das ordens em voo —
     *                         usado pela {@link CapitalReservationPolicy} para calcular
     *                         se o novo BUY ultrapassa o limite de alocacao do runner
     */
    public record ExposureSnapshot(int openPositions, int inFlightOrders, BigDecimal inFlightExposure) {

        /** Ao menos uma posicao aberta ou ordem em voo — usado pela politica SINGLE. */
        public boolean hasOpenOrInFlight() {
            return openPositions > 0 || inFlightOrders > 0;
        }
    }
}
