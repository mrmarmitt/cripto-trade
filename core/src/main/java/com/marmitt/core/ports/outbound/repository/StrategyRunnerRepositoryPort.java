package com.marmitt.core.ports.outbound.repository;

import com.marmitt.core.domain.runner.Position;
import com.marmitt.core.domain.runner.StrategyRunner;
import com.marmitt.core.domain.runner.Transaction;
import com.marmitt.core.domain.runner.TransactionMatch;
import com.marmitt.core.enums.TransactionStatus;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Porta de saída para persistência do agregado StrategyRunner.
 * Cobre o aggregate root e todas as entidades filhas:
 * {@link Position}, {@link Transaction} e {@link TransactionMatch}.
 * <p>
 * Convenções:
 * <ul>
 *   <li>Métodos {@code save*} fazem upsert (INSERT ou UPDATE)</li>
 *   <li>Métodos {@code find*} retornam {@code Optional} para entidades únicas
 *       e {@code List} para coleções (nunca {@code null})</li>
 *   <li>Operações atômicas que envolvem múltiplas entidades são prefixadas com {@code saveAtomic*}</li>
 * </ul>
 *
 * @see <a href="docs/IMPLEMENTATION_GUIDE.md">IG Seções 3.3, 6.2, 6.6</a>
 */
public interface StrategyRunnerRepositoryPort {

    /**
     * Persiste o StrategyRunner (INSERT ou UPDATE).
     * Usa optimistic locking via campo {@code version}.
     */
    void save(StrategyRunner runner);

    /** Busca Runner por PK. */
    Optional<StrategyRunner> findById(UUID runnerId);

    /**
     * Busca todos os Runners de um Portfolio.
     * Usado pelo Portfolio para listar e rotear callbacks.
     */
    List<StrategyRunner> findByPortfolioId(UUID portfolioId);

    /**
     * Busca Runners operacionais de um Portfolio (status ACTIVE ou HALTED).
     * Usado pelo Portfolio para roteamento de callbacks da exchange.
     *
     * @see com.marmitt.core.enums.RunnerStatus#isOperational()
     */
    List<StrategyRunner> findOperationalByPortfolioId(UUID portfolioId);

    /**
     * Busca Runners operacionais pelo símbolo e exchange, filtrando apenas portfolios ativos.
     * <p>
     * Faz JOIN com o Portfolio para garantir que apenas Runners de portfolios com
     * {@code isActive = true} sejam retornados. O filtro {@code canAcceptSignals()}
     * é aplicado em memória pelo caller após esta query.
     * <p>
     * Usado por {@link com.marmitt.core.application.listener.runner.PortfolioStrategyRunnerPriceUpdateListener}
     * para rotear market data aos Runners elegíveis.
     *
     * @param symbol     par de trading (ex: "BTCUSDT")
     * @param exchangeId identificador da exchange (ex: "BINANCE")
     * @return lista de Runners com {@code status.isOperational()} de portfolios ativos
     * @see com.marmitt.core.enums.RunnerStatus#isOperational()
     */
    List<StrategyRunner> findOperationalBySymbol(String symbol, String exchangeId);

    /**
     * Busca Runner pelo shortCode dentro de um Portfolio.
     * Usado pelo Portfolio para rotear callbacks via {@code clientOrderId}
     * ({@code v1r{shortCode}...}).
     *
     * @see <a href="docs/IMPLEMENTATION_GUIDE.md">IG Seção 5.6 (Roteamento de Callbacks)</a>
     */
    Optional<StrategyRunner> findByShortCodeAndPortfolioId(String shortCode, UUID portfolioId);

    /**
     * Persiste a Position (INSERT ou UPDATE).
     * Usa optimistic locking via campo {@code version}.
     */
    void savePosition(Position position);

    /**
     * Tenta inserir uma nova Position OPEN.
     * Retorna false quando outra Position OPEN do mesmo runner/simbolo ja existe.
     */
    boolean trySavePosition(Position position);

    /** Busca Position por PK. */
    Optional<Position> findPositionById(UUID positionId);

    /**
     * Busca todas as Positions OPEN de um Runner.
     * Usado pelo Runner para verificar posições abertas antes de novos sinais.
     */
    List<Position> findOpenPositionsByRunnerId(UUID runnerId);

    /**
     * Busca a Position OPEN de um Runner para um símbolo específico.
     * Retorna {@code Optional.empty()} se não há posição aberta para o par.
     * Usado na lógica de Execution Policy (Single mode).
     */
    Optional<Position> findOpenPositionByRunnerIdAndSymbol(UUID runnerId, String symbol);

    /**
     * Persiste a Transaction (INSERT ou UPDATE).
     * Usa optimistic locking via campo {@code version}.
     */
    void saveTransaction(Transaction transaction);

    /** Busca Transaction por PK. */
    Optional<Transaction> findTransactionById(UUID transactionId);

    /**
     * Busca Transaction pelo clientOrderId.
     * <b>Operação crítica</b> — usada para:
     * <ul>
     *   <li>Roteamento de callbacks da exchange (Portfolio → Runner)</li>
     *   <li>Idempotência: verificar se a Transaction já existe antes de criar</li>
     *   <li>Reconciliação no Boot Sequence (consulta por clientOrderId)</li>
     * </ul>
     *
     * @see <a href="docs/IMPLEMENTATION_GUIDE.md">IG Seção 5.6, 6.6</a>
     */
    Optional<Transaction> findTransactionByClientOrderId(String clientOrderId);

    /**
     * Busca Transactions de um Runner nos status especificados.
     * Usos principais:
     * <ul>
     *   <li>Boot Sequence Step 1: {@code PENDING} sem exchangeOrderId (zumbis)</li>
     *   <li>Boot Sequence Step 2: {@code PENDING, SUBMITTED, PARTIAL} (limbo)</li>
     *   <li>Watchdog: {@code SUBMITTED, PARTIAL} para monitoramento de timeout</li>
     * </ul>
     *
     * @see <a href="docs/IMPLEMENTATION_GUIDE.md">IG Seção 6.6.2</a>
     */
    List<Transaction> findByRunnerIdAndStatuses(UUID runnerId, Collection<TransactionStatus> statuses);

    /**
     * Persiste o TransactionMatch (INSERT apenas — imutável após criação).
     */
    void saveTransactionMatch(TransactionMatch match);

    /**
     * Verifica se já existe um TransactionMatch com o {@code matchId} fornecido.
     * Usado pelo Portfolio para garantir idempotência do {@code confirmExecution()}:
     * se {@code true}, o evento é descartado silenciosamente.
     *
     * @see <a href="docs/IMPLEMENTATION_GUIDE.md">IG Seção 5.5.1</a>
     */
    boolean existsTransactionMatchById(UUID matchId);

    /**
     * Busca todos os matches de uma Transaction.
     * Usado para cálculo de PnL realizado e auditoria.
     */
    List<TransactionMatch> findMatchesByTransactionId(UUID transactionId);

    /**
     * Persiste atomicamente uma Transaction PENDING e aplica o lock na Position alvo.
     * Implementa os passos 6a e 6b do protocolo Persist-First (IG Seção 6.2.1):
     * <ol>
     *   <li>Salva a Transaction com status PENDING</li>
     *   <li>Salva a Position com os campos de locking preenchidos</li>
     * </ol>
     * Executado dentro de uma única transação de banco, garantindo atomicidade.
     * Chamado apenas para operações de SELL que requerem lock de lote.
     *
     * @see <a href="docs/IMPLEMENTATION_GUIDE.md">IG Seção 6.2.1 (passos 6a-6b)</a>
     */
    void saveAtomicTransactionAndPositionLock(Transaction transaction, Position lockedPosition);

    /**
     * Aplica lock atomico em uma Position OPEN para uma SELL.
     * Retorna true se o lock foi aplicado, false se a position ja foi lockada
     * ou nao esta mais OPEN.
     */
    boolean tryLockPositionForSell(UUID positionId, UUID transactionId, java.math.BigDecimal quantity);

    /**
     * Persiste atomicamente uma Transaction atualizada e seu novo TransactionMatch.
     * Usado após recebimento de callback de execução (PARTIAL ou FILLED):
     * garante que o status da Transaction e o match sejam salvos juntos.
     *
     * @see <a href="docs/IMPLEMENTATION_GUIDE.md">IG Seção 6.2.1</a>
     */
    void saveAtomicTransactionAndMatch(Transaction transaction, TransactionMatch match);
}
