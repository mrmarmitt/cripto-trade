package com.marmitt.core.ports.inbound.portfolio;

import com.marmitt.core.dto.capital.MarginRelease;

/**
 * Port de entrada para liberação (estorno) de margem reservada.
 * <p>
 * <b>Natureza:</b> Assíncrono com retries ilimitados — capital preso (starvation) é inaceitável.
 * <p>
 * Deve ser invocado quando a Transaction atinge REJECTED, CANCELED ou EXPIRED.
 * O Runner persiste o evento localmente para garantir reenvio em caso de crash.
 * O Portfolio garante idempotência via {@code transactionId}.
 * <p>
 * Efeito no {@code GlobalBalance}: devolve margem de Reserved → Available.
 * {@code reserved -= releaseAmount}, {@code available += releaseAmount}.
 * Para REJECTED/EXPIRED: estorno total. Para CANCELED parcial: estorno proporcional.
 *
 * @see <a href="docs/IMPLEMENTATION_GUIDE.md">IG Seção 5.2.3</a>
 */
public interface ReleaseMarginPort {

    /**
     * Solicita devolução de margem reservada.
     *
     * @param release payload com transactionId, releaseAmount, motivo e executedAmount (parcial)
     */
    void release(MarginRelease release);
}
