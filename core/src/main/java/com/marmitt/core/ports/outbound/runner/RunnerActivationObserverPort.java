package com.marmitt.core.ports.outbound.runner;

import java.util.UUID;

/**
 * Avisado quando um runner passa a operar ({@code start} ou {@code resume}) (T56). O ciclo de vida nao
 * sabe quem observa; a composicao liga o coordenador de market data aqui para nao esperar o ciclo.
 */
@FunctionalInterface
public interface RunnerActivationObserverPort {

    RunnerActivationObserverPort NONE = runnerId -> {
    };

    void onActivated(UUID runnerId);
}
