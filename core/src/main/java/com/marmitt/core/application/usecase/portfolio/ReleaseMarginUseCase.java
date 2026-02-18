package com.marmitt.core.application.usecase.portfolio;

import com.marmitt.core.dto.capital.MarginRelease;
import com.marmitt.core.ports.inbound.portfolio.ReleaseMarginPort;

/**
 * Implementação de {@link ReleaseMarginPort} — Estorno de margem assíncrono.
 * <b>Não implementado — ver F1-11.</b>
 *
 * @see <a href="docs/IMPLEMENTATION_GUIDE.md">IG Seção 5.2.3</a>
 */
public class ReleaseMarginUseCase implements ReleaseMarginPort {

    /**
     * @throws UnsupportedOperationException sempre — implementação prevista para F1-11
     */
    @Override
    public void release(MarginRelease release) {
        throw new UnsupportedOperationException(
                "release not yet implemented — scheduled for F1-11");
    }
}
