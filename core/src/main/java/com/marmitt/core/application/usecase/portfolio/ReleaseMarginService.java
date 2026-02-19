package com.marmitt.core.application.usecase.portfolio;

import com.marmitt.core.dto.capital.MarginRelease;
import com.marmitt.core.dto.events.MarginReleaseEvent;
import com.marmitt.core.ports.outbound.events.EventPublisherPort;
import lombok.extern.slf4j.Slf4j;

/**
 * Serviço de publicação de estorno de margem — lado Runner (F1-11).
 * <p>
 * Responsabilidade: publicar {@link MarginReleaseEvent} de forma fire-and-forget.
 * O processamento contábil (Reserved → Available no GlobalBalance) ocorre no Portfolio
 * via {@code HandleMarginReleaseService}, disparado pelo listener Spring após o commit.
 * <p>
 * Criticidade: capital preso (starvation) é inaceitável — o listener utiliza
 * retries ilimitados com backoff exponencial até o processamento bem-sucedido.
 *
 * @implNote Pertence ao fluxo <b>HandleOrderTermination</b> — lado Runner (publicação do evento).
 *           Será absorvido por esse fluxo em refatoração futura.
 *           O Runner deve persistir o evento localmente antes de publicar para garantir
 *           reenvio em caso de crash (Boot Sequence reconcilia os gaps). Implementação
 *           de persistência local prevista para V2+ com Outbox Pattern.
 * @see <a href="docs/IMPLEMENTATION_GUIDE.md">IG Seção 5.2.3, 5.3.1</a>
 */
@Slf4j
public class ReleaseMarginService {

    private final EventPublisherPort eventPublisher;

    public ReleaseMarginService(EventPublisherPort eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    /**
     * Publica o evento de estorno de margem.
     * Retorna imediatamente — o processamento pelo Portfolio é assíncrono.
     *
     * @param release payload com transactionId, releaseAmount, reason e executedAmount
     */
    public void release(MarginRelease release) {
        log.debug("release: publishing event transactionId={} amount={} reason={}",
                release.transactionId(), release.releaseAmount(), release.reason());
        eventPublisher.publishEvent(new MarginReleaseEvent(release));
    }
}
