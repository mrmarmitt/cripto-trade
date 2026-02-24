package com.marmitt.core.ports.outbound.repository;

import com.marmitt.core.domain.portfolio.GlobalBalance;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * Porta de saída para persistência do {@link GlobalBalance}.
 * <p>
 * O método crítico é {@link #reserveAtomic}, que implementa o ponto de serialização
 * do Capital Request (IG Seção 5.2.1). A implementação de infraestrutura deve executar
 * um UPDATE atômico com lock pessimista:
 * <pre>
 *   UPDATE global_balances
 *      SET available_balance = available_balance - :amount,
 *          reserved_balance  = reserved_balance  + :amount
 *    WHERE portfolio_id = :id
 *      AND available_balance >= :amount
 * </pre>
 * Retorna {@code true} se exatamente 1 linha foi afetada (reserva bem-sucedida),
 * {@code false} se a condição {@code available_balance >= amount} falhou (saldo insuficiente).
 *
 * @see <a href="docs/IMPLEMENTATION_GUIDE.md">IG Seção 5.2.1</a>
 */
public interface GlobalBalanceRepositoryPort {

    /**
     * Busca o GlobalBalance de um Portfolio.
     */
    Optional<GlobalBalance> findByPortfolioId(UUID portfolioId);

    /**
     * Persiste o GlobalBalance (INSERT ou UPDATE).
     * Usa optimistic locking via campo {@code version}.
     */
    void save(GlobalBalance balance);

    /**
     * Reserva capital atomicamente via lock pessimista no banco.
     * <p>
     * Move {@code amount} de {@code available_balance} → {@code reserved_balance}
     * em um único UPDATE atômico. O lock pessimista garante que operações concorrentes
     * não causem double-spend.
     * <p>
     * <b>Contrato:</b>
     * <ul>
     *   <li>Retorna {@code true} se a reserva foi aplicada (available >= amount)</li>
     *   <li>Retorna {@code false} se o saldo disponível é insuficiente</li>
     *   <li>Nunca lança exceção por saldo insuficiente — o boolean é o contrato</li>
     * </ul>
     *
     * @param portfolioId portfolio alvo da reserva
     * @param amount      valor a reservar — deve ser positivo e já incluir o safety buffer do Runner
     *                    (calculado pelo Runner antes de invocar {@code ProcessTradeSignalUseCase.persistBuyAndReserve()})
     * @return {@code true} se reserva aplicada com sucesso; {@code false} se saldo insuficiente
     * @see <a href="docs/IMPLEMENTATION_GUIDE.md">IG Seção 5.2.1 (Thread-safety)</a>
     */
    boolean reserveAtomic(UUID portfolioId, BigDecimal amount);
}
