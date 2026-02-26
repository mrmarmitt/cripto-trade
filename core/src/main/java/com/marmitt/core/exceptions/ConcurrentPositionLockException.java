package com.marmitt.core.exceptions;

import java.util.UUID;

/**
 * Lancada quando duas threads concorrentes tentam bloquear a mesma posicao
 * para SELL simultaneamente e a segunda falha no lock otimista.
 *
 * <p>Representa uma colisao esperada em sistemas concorrentes: o lock otimista
 * da primeira thread ja protegeu a integridade dos dados. A thread que recebe
 * esta excecao deve descartar o tick silenciosamente — a posicao ja esta sendo
 * processada por outra thread.
 *
 * <p>Esta excecao e lancada pelo adapter de persistencia ao detectar
 * {@code OptimisticLockingFailureException} em {@code saveAtomicTransactionAndPositionLock},
 * permitindo que o use case a trate sem depender de classes do Spring.
 */
public class ConcurrentPositionLockException extends RuntimeException {

    public ConcurrentPositionLockException(UUID positionId, UUID runnerId) {
        super("Concurrent SELL lock conflict: positionId=" + positionId + " runnerId=" + runnerId
                + " — another thread already holds the lock");
    }
}
