package com.marmitt.application.spring.bootstrap;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Processamento de ticks pelos runners (T42, IG §12.10).
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "runner.signal")
public class RunnerSignalProperties {

    /** Threads que avaliam os runners (cada runner usa no máximo uma por vez). */
    private int threads = 4;
    /** Idade máxima do tick (desde o recebimento) ao começar a avaliação; mais velho é descartado. */
    private long staleTickThresholdMs = 5_000L;
    /** Avaliação mais longa que isto gera WARN (não é abortada: pode estar no meio de uma transação). */
    private long slowProcessingWarnMs = 1_000L;
}
