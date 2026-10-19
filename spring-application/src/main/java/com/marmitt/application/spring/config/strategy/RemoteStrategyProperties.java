package com.marmitt.application.spring.config.strategy;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Catálogo estático e parâmetros do cliente de estratégias remotas (T41).
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
    /** Intervalo para tentar de novo o registro de entradas ainda não registradas. */
    private long registrationRetryIntervalMs = 30_000L;
    /** Token de serviço enviado como {@code Authorization: Bearer}. */
    private String token;
    private List<Entry> catalog = new ArrayList<>();

    @Getter
    @Setter
    public static class Entry {
        private UUID strategyRef;
        private String baseUrl;
        private String name;
        private String version;
        private boolean enabled = true;
    }
}
