package com.marmitt.core.ports.inbound.portfolio;

import com.marmitt.core.dto.portfolio.request.ChangeSafeModeRequest;
import com.marmitt.core.dto.portfolio.response.SafeModeStatusDto;
import com.marmitt.core.enums.SafeModeOrigin;

import java.util.UUID;

/**
 * Consulta e mudança do Safe Mode de um portfolio (T45). A API admin usa {@code MANUAL}; os
 * gatilhos automáticos (T43, T25) usam {@code AUTOMATIC} e só conseguem acionar {@code HALT}.
 */
public interface ManageSafeModePort {

    SafeModeStatusDto status(UUID portfolioId);

    /**
     * Muda o nível de forma idempotente (mesmo nível não gera mudança nem auditoria). A execução
     * dos níveis de ação em massa é assíncrona: acontece nos ticks dos runners; o retorno traz o
     * progresso no momento da chamada.
     */
    SafeModeStatusDto change(UUID portfolioId, SafeModeOrigin origin, ChangeSafeModeRequest request);
}
