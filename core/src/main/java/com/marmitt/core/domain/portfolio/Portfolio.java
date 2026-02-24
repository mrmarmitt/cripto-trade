package com.marmitt.core.domain.portfolio;

import com.marmitt.core.enums.CapitalPoolingMode;
import com.marmitt.core.enums.SafeModeStatus;
import lombok.Getter;

import java.time.Instant;
import java.util.Objects;
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
            CapitalPoolingMode capitalPoolingMode
    ) {
        this.id = id;
        this.name = name;
        this.isActive = isActive;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt cannot be null");
        this.safeModeStatus = Objects.requireNonNull(safeModeStatus, "safeModeStatus cannot be null");
        this.capitalPoolingMode = Objects.requireNonNull(capitalPoolingMode, "capitalPoolingMode cannot be null");
    }

    /**
     * Atualiza o nível do Safe Mode do Portfolio.
     *
     * @see <a href="docs/IMPLEMENTATION_GUIDE.md">IG Seção 3.2.1, Blueprint 11.1.B.1</a>
     */
    public void setSafeModeStatus(SafeModeStatus status) {
        this.safeModeStatus = Objects.requireNonNull(status, "SafeModeStatus cannot be null");
    }

}
