package com.marmitt.application.spring.config.strategy;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Parâmetros do cliente de estratégias remotas (T41). O catálogo e o token de cada estratégia ficam no
 * banco e são administrados por API (T54).
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "ctrade.strategy.remote")
public class RemoteStrategyProperties {

    /** Timeout rígido de cada chamada ao serviço (sem retry). */
    private long timeoutMs = 250L;
    /** Idade máxima da decisão, contada desde o tick; mais velha vira HOLD. */
    private long maxDecisionAgeMs = 2_000L;
    /** Intervalo do ciclo que alinha as estratégias em uso com o catálogo e repete conferências pendentes. */
    private long registrationRetryIntervalMs = 30_000L;
}
