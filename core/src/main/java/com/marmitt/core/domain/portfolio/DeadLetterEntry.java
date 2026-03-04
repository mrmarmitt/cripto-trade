package com.marmitt.core.domain.portfolio;

import com.marmitt.core.enums.DlqReason;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Captura execuções órfãs, IDs inválidos ou transações irreconciliáveis
 * para intervenção manual.
 * <p>
 * Criado pelo Portfolio (Router) quando um callback da exchange não pode
 * ser roteado para o Runner correto.
 *
 * @see <a href="docs/IMPLEMENTATION_GUIDE.md">IG Seção 3.2.5, Blueprint 4.D, 10.3.D</a>
 */
public class DeadLetterEntry {

    private final UUID id;
    private final UUID portfolioId;
    private final UUID runnerId;

    /**
     * clientOrderId extraído do payload. Nullable se malformado.
     */
    private final String clientOrderId;

    /**
     * ID atribuído pela exchange. Nullable se não disponível no payload.
     */
    private final String exchangeOrderId;

    /**
     * JSON original do callback da exchange — preservado integralmente para reprocessamento.
     */
    private final String rawPayload;

    private final DlqReason reason;
    private boolean isResolved;

    /**
     * Operador que resolveu manualmente. Nullable.
     */
    private String resolvedBy;

    private Instant resolvedAt;
    private final Instant createdAt;

    /**
     * Construtor para criação de novo registro DLQ.
     */
    public DeadLetterEntry(
            UUID portfolioId,
            String clientOrderId,
            String exchangeOrderId,
            String rawPayload,
            DlqReason reason
    ) {
        this(
                portfolioId,
                null,
                clientOrderId,
                exchangeOrderId,
                rawPayload,
                reason
        );
    }

    /**
     * Construtor para criaÃ§Ã£o de novo registro DLQ com vinculo opcional ao runner.
     */
    public DeadLetterEntry(
            UUID portfolioId,
            UUID runnerId,
            String clientOrderId,
            String exchangeOrderId,
            String rawPayload,
            DlqReason reason
    ) {
        this.id = UUID.randomUUID();
        this.portfolioId = Objects.requireNonNull(portfolioId, "portfolioId cannot be null");
        this.runnerId = runnerId;
        this.clientOrderId = clientOrderId;
        this.exchangeOrderId = exchangeOrderId;
        this.rawPayload = Objects.requireNonNull(rawPayload, "rawPayload cannot be null");
        this.reason = Objects.requireNonNull(reason, "reason cannot be null");
        this.isResolved = false;
        this.resolvedBy = null;
        this.resolvedAt = null;
        this.createdAt = Instant.now();
    }

    /**
     * Construtor completo para reconstituição a partir do banco de dados.
     */
    public DeadLetterEntry(
            UUID id,
            UUID portfolioId,
            String clientOrderId,
            String exchangeOrderId,
            String rawPayload,
            DlqReason reason,
            boolean isResolved,
            String resolvedBy,
            Instant resolvedAt,
            Instant createdAt
    ) {
        this(
                id,
                portfolioId,
                null,
                clientOrderId,
                exchangeOrderId,
                rawPayload,
                reason,
                isResolved,
                resolvedBy,
                resolvedAt,
                createdAt
        );
    }

    /**
     * Construtor completo para reconstituiÃ§Ã£o a partir do banco de dados com runner.
     */
    public DeadLetterEntry(
            UUID id,
            UUID portfolioId,
            UUID runnerId,
            String clientOrderId,
            String exchangeOrderId,
            String rawPayload,
            DlqReason reason,
            boolean isResolved,
            String resolvedBy,
            Instant resolvedAt,
            Instant createdAt
    ) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.portfolioId = Objects.requireNonNull(portfolioId, "portfolioId cannot be null");
        this.runnerId = runnerId;
        this.clientOrderId = clientOrderId;
        this.exchangeOrderId = exchangeOrderId;
        this.rawPayload = Objects.requireNonNull(rawPayload, "rawPayload cannot be null");
        this.reason = Objects.requireNonNull(reason, "reason cannot be null");
        this.isResolved = isResolved;
        this.resolvedBy = resolvedBy;
        this.resolvedAt = resolvedAt;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt cannot be null");
    }

    /**
     * Marca a entrada como resolvida por um operador.
     *
     * @param operator identificador do operador (nome, email, sistema)
     */
    public void resolve(String operator) {
        if (this.isResolved) {
            throw new IllegalStateException("DeadLetterEntry already resolved: " + id);
        }
        Objects.requireNonNull(operator, "operator cannot be null");
        this.isResolved = true;
        this.resolvedBy = operator;
        this.resolvedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getPortfolioId() { return portfolioId; }
    public UUID getRunnerId() { return runnerId; }
    public String getClientOrderId() { return clientOrderId; }
    public String getExchangeOrderId() { return exchangeOrderId; }
    public String getRawPayload() { return rawPayload; }
    public DlqReason getReason() { return reason; }
    public boolean isResolved() { return isResolved; }
    public String getResolvedBy() { return resolvedBy; }
    public Instant getResolvedAt() { return resolvedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
