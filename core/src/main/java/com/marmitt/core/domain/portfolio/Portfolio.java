package com.marmitt.core.domain.portfolio;

import com.marmitt.core.enums.CapitalPoolingMode;
import com.marmitt.core.enums.SafeModeOrigin;
import com.marmitt.core.enums.SafeModeStatus;
import com.marmitt.core.exceptions.SafeModeTransitionException;
import lombok.Getter;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Getter
public class Portfolio {

    private final UUID id;
    private final String name;
    private boolean isActive;
    private final Instant createdAt;

    // Novos campos — modelo alvo (IG 3.2.1)
    private SafeModeStatus safeModeStatus;
    private CapitalPoolingMode capitalPoolingMode;

    /** Quem fez a última mudança do Safe Mode; nulo se nunca mudou (T45). */
    private SafeModeOrigin safeModeOrigin;
    private String safeModeReason;
    private Instant safeModeChangedAt;

    private final Long version;

    /**
     * Construtor de criação — para novos portfolios.
     */
    public Portfolio(
            UUID id,
            String name
    ) {
        this.id = id;
        this.name = name;
        this.isActive = true;
        this.createdAt = Instant.now();
        this.safeModeStatus = SafeModeStatus.NORMAL;
        this.capitalPoolingMode = CapitalPoolingMode.SHARED;
        this.version = null;
    }

    /**
     * Construtor de reconstituição — para carregar do banco de dados.
     */
    public Portfolio(
            UUID id,
            String name,
            boolean isActive,
            Instant createdAt,
            SafeModeStatus safeModeStatus,
            CapitalPoolingMode capitalPoolingMode,
            SafeModeOrigin safeModeOrigin,
            String safeModeReason,
            Instant safeModeChangedAt,
            Long version
    ) {
        this.id = id;
        this.name = name;
        this.isActive = isActive;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt cannot be null");
        this.safeModeStatus = Objects.requireNonNull(safeModeStatus, "safeModeStatus cannot be null");
        this.capitalPoolingMode = Objects.requireNonNull(capitalPoolingMode, "capitalPoolingMode cannot be null");
        this.safeModeOrigin = safeModeOrigin;
        this.safeModeReason = safeModeReason;
        this.safeModeChangedAt = safeModeChangedAt;
        this.version = version;
    }

    /**
     * Muda o nível do Safe Mode (Blueprint 11.1.B.1 e 11.1.C, T45).
     *
     * <ul>
     *   <li>Mesmo nível: nenhuma mudança (idempotente).</li>
     *   <li>Subida: pode pular níveis; {@code CANCEL_ALL} e {@code PANIC_SELL} exigem confirmação.</li>
     *   <li>Descida: um nível por vez (3→2→1→NORMAL).</li>
     *   <li>{@code AUTOMATIC} só sobe até {@code HALT}; acima disso, ou já em HALT ou mais, não muda.</li>
     * </ul>
     *
     * @return o registro de auditoria da mudança, ou vazio se o nível já era o pedido
     * @throws SafeModeTransitionException se a transição violar as regras acima
     */
    public Optional<SafeModeChange> changeSafeMode(SafeModeStatus target, SafeModeOrigin origin,
                                                   String reason, boolean confirmed) {
        Objects.requireNonNull(target, "target cannot be null");
        Objects.requireNonNull(origin, "origin cannot be null");
        if (reason == null || reason.isBlank()) {
            throw new SafeModeTransitionException("A reason is required to change the safe mode");
        }
        if (target == safeModeStatus) {
            return Optional.empty();
        }
        if (origin == SafeModeOrigin.AUTOMATIC) {
            if (target != SafeModeStatus.HALT) {
                throw new SafeModeTransitionException(
                        "Automatic triggers can only raise the safe mode to HALT, requested " + target);
            }
            if (safeModeStatus.isActive()) {
                return Optional.empty();
            }
        }
        boolean raising = target.compareTo(safeModeStatus) > 0;
        if (raising && target.requiresConfirmation() && !confirmed) {
            throw new SafeModeTransitionException(target + " requires explicit confirmation");
        }
        if (!raising && safeModeStatus.ordinal() - target.ordinal() > 1) {
            throw new SafeModeTransitionException(
                    "Safe mode must be lowered one level at a time: " + safeModeStatus + " -> " + target);
        }

        SafeModeChange change = new SafeModeChange(UUID.randomUUID(), id, safeModeStatus, target, origin,
                reason, Instant.now());
        this.safeModeStatus = target;
        this.safeModeOrigin = origin;
        this.safeModeReason = reason;
        this.safeModeChangedAt = change.changedAt();
        return Optional.of(change);
    }
}
