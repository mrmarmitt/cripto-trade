package com.marmitt.core.application.usecase.runner.processsignal;

import com.marmitt.core.domain.runner.Position;
import com.marmitt.core.domain.runner.StrategyRunner;
import com.marmitt.core.domain.runner.Transaction;
import com.marmitt.core.enums.TransactionStatus;
import com.marmitt.core.ports.outbound.repository.StrategyRunnerRepositoryPort;

import java.math.BigDecimal;
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
 *   <li>{@link CapitalReservationPolicy}: para calcular se o novo BUY ultrapassaria
 *       o limite de alocacao maxima ({@code maxAllocationPercent}) do runner.</li>
 * </ul>
 *
 * <p>{@link #loadSnapshot} e o metodo preferido no caminho BUY com politica SINGLE,
 * pois carrega posicoes e transacoes em uma unica chamada e compartilha o resultado
 * entre a policy e a reserva — evitando consultas duplicadas ao banco.
 */
class RunnerExposureService {

    private final StrategyRunnerRepositoryPort strategyRunnerRepository;

    public RunnerExposureService(StrategyRunnerRepositoryPort strategyRunnerRepository) {
        this.strategyRunnerRepository = strategyRunnerRepository;
    }

    /**
     * Calcula o valor financeiro total em voo para o runner.
     * Usado como fallback quando o snapshot nao foi pre-carregado
     * (ex: runners com politica MULTIPLE que nao precisam do {@code hasOpenOrInFlight}).
     */
    public BigDecimal calculateInFlightExposure(UUID runnerId) {
        List<Transaction> inflight = strategyRunnerRepository.findByRunnerIdAndStatuses(runnerId, List.of(
                TransactionStatus.PENDING,
                TransactionStatus.SUBMITTED,
                TransactionStatus.PARTIAL
        ));
        return inflight.stream()
                .map(Transaction::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
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
        List<Transaction> inFlight = strategyRunnerRepository.findByRunnerIdAndStatuses(runner.getId(), List.of(
                TransactionStatus.PENDING,
                TransactionStatus.SUBMITTED,
                TransactionStatus.PARTIAL
        ));

        BigDecimal inFlightExposure = inFlight.stream()
                .map(Transaction::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        boolean hasOpenOrInFlight = !openPositions.isEmpty() || !inFlight.isEmpty();
        return new ExposureSnapshot(hasOpenOrInFlight, inFlightExposure);
    }

    /**
     * Foto do estado de exposicao do runner no momento do tick.
     *
     * @param hasOpenOrInFlight {@code true} se existe ao menos uma posicao aberta ou
     *                          ordem em voo — usado pela policy SINGLE para bloquear novo BUY
     * @param inFlightExposure  somatorio dos valores totais das ordens em voo —
     *                          usado pela {@link CapitalReservationPolicy} para calcular
     *                          se o novo BUY ultrapassa o limite de alocacao do runner
     */
    public record ExposureSnapshot(boolean hasOpenOrInFlight, BigDecimal inFlightExposure) {
    }
}
