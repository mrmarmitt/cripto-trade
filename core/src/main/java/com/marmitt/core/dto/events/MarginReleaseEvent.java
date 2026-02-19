package com.marmitt.core.dto.events;

import com.marmitt.core.dto.capital.MarginRelease;

import java.util.Objects;

/**
 * Evento publicado pelo Runner quando uma Transaction atinge estado terminal
 * (REJECTED, CANCELED ou EXPIRED). Dispara o estorno de margem no Portfolio (Reserved → Available).
 * <p>
 * Publicado via {@code EventPublisherPort} e processado por {@code HandleMarginReleaseService}
 * com retries ilimitados — capital preso (starvation) é inaceitável.
 * A idempotência é garantida por verificação de saldo disponível na reserva.
 *
 * @see <a href="docs/IMPLEMENTATION_GUIDE.md">IG Seção 5.2.3, 5.3.1</a>
 */
public record MarginReleaseEvent(MarginRelease release) {

    public MarginReleaseEvent {
        Objects.requireNonNull(release, "release cannot be null");
    }
}
