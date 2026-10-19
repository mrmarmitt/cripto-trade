package com.marmitt.core.application.usecase.runner.processsignal;

import com.marmitt.core.dto.websocket.data.MarketDataDto;
import com.marmitt.core.ports.outbound.metrics.SignalMetricsPort;
import com.marmitt.core.ports.outbound.metrics.TickDiscardReason;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.BiConsumer;

/**
 * Mailbox por runner com capacidade 1 e politica latest-wins (IG §12.1–12.2, T42).
 *
 * <ul>
 *   <li><b>Sequencial por runner:</b> no maximo uma avaliacao do mesmo runner em execucao; ticks do
 *       mesmo runner nunca rodam em paralelo.</li>
 *   <li><b>Coalescencia:</b> tick que chega enquanto outro aguarda substitui o pendente — o runner
 *       sempre avalia o mais recente; o substituido conta como {@link TickDiscardReason#COALESCED}.</li>
 *   <li><b>Ordem:</b> tick com timestamp anterior ao ultimo aceito do runner e descartado
 *       ({@link TickDiscardReason#OUT_OF_ORDER}) — estrategias com estado nunca veem a serie voltar.</li>
 *   <li><b>Justica:</b> cada tarefa avalia um tick e, havendo outro pendente, se reagenda no fim da
 *       fila; um runner com feed intenso nao monopoliza a thread.</li>
 * </ul>
 * A thread que entrega o tick so enfileira: a avaliacao corre no executor dos runners.
 */
@Slf4j
class RunnerMailbox {

    private final Executor executor;
    private final BiConsumer<UUID, MarketDataDto> evaluation;
    private final SignalMetricsPort metrics;
    private final Map<UUID, Slot> slots = new ConcurrentHashMap<>();

    RunnerMailbox(Executor executor, BiConsumer<UUID, MarketDataDto> evaluation, SignalMetricsPort metrics) {
        this.executor = executor;
        this.evaluation = evaluation;
        this.metrics = metrics;
    }

    void submit(UUID runnerId, MarketDataDto tick) {
        Slot slot = slots.computeIfAbsent(runnerId, id -> new Slot());
        boolean coalesced;
        boolean schedule;
        synchronized (slot) {
            if (slot.lastAccepted != null && tick.timestamp().isBefore(slot.lastAccepted.timestamp())) {
                metrics.recordTickDiscarded(runnerId, TickDiscardReason.OUT_OF_ORDER);
                return;
            }
            slot.lastAccepted = tick;
            coalesced = slot.pending != null;
            slot.pending = tick;
            schedule = !slot.scheduled;
            slot.scheduled = true;
        }
        if (coalesced) {
            metrics.recordTickDiscarded(runnerId, TickDiscardReason.COALESCED);
        }
        if (schedule) {
            schedule(runnerId, slot);
        }
    }

    private void schedule(UUID runnerId, Slot slot) {
        try {
            executor.execute(() -> drainOne(runnerId, slot));
        } catch (RejectedExecutionException e) {
            synchronized (slot) {
                slot.scheduled = false;
            }
            log.error("mailbox: runner executor rejected runnerId={} - tick kept for the next submit", runnerId, e);
        }
    }

    private void drainOne(UUID runnerId, Slot slot) {
        MarketDataDto tick;
        synchronized (slot) {
            tick = slot.pending;
            slot.pending = null;
        }
        if (tick != null) {
            try {
                evaluation.accept(runnerId, tick);
            } catch (RuntimeException e) {
                log.error("mailbox: evaluation failed runnerId={} - {}", runnerId, e.getMessage(), e);
            }
        }
        boolean more;
        synchronized (slot) {
            more = slot.pending != null;
            slot.scheduled = more;
        }
        if (more) {
            schedule(runnerId, slot);
        }
    }

    private static final class Slot {
        private MarketDataDto pending;
        private MarketDataDto lastAccepted;
        private boolean scheduled;
    }
}
