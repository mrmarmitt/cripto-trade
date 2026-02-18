package com.marmitt.core.application.usecase.portfolio;

import com.marmitt.core.dto.capital.MarginRelease;
import com.marmitt.core.ports.inbound.portfolio.ReleaseMarginPort;

/**
 * Implementação de {@link ReleaseMarginPort} — Estorno de margem assíncrono.
 *
 * @implNote Implementação pendente para F1-11. Quando implementado, devolverá margem de
 *           Reserved → Available no {@code GlobalBalance} via {@code ApplicationEventPublisher}
 *           com retries ilimitados (capital preso é inaceitável — IG Seção 5.2.3).
 * @see <a href="docs/IMPLEMENTATION_GUIDE.md">IG Seção 5.2.3</a>
 */
public class ReleaseMarginUseCase implements ReleaseMarginPort {

    /**
     * @implNote Não implementado — ver F1-11.
     * @throws UnsupportedOperationException sempre
     */
    @Override
    public void release(MarginRelease release) {
        throw new UnsupportedOperationException(
                "release not yet implemented — scheduled for F1-11");
    }
}
